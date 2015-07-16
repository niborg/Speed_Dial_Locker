package com.vitaminbacon.lockscreendialer.fragments;


import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.vitaminbacon.lockscreendialer.AppBackgroundActivity;
import com.vitaminbacon.lockscreendialer.KeypadPatternConfigActivity;
import com.vitaminbacon.lockscreendialer.KeypadPinConfigActivity;
import com.vitaminbacon.lockscreendialer.R;
import com.vitaminbacon.lockscreendialer.helpers.BitmapToViewHelper;
import com.vitaminbacon.lockscreendialer.services.LockScreenService;
import com.vitaminbacon.lockscreendialer.views.ColorPreference;
import com.vitaminbacon.lockscreendialer.views.MyListPreference;

/**
 * A simple {@link Fragment} subclass.
 */
public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener,
        MyListPreference.ListItemClickListener, ColorPickerDialogFragment.OnNoColorSelectedListener,
        BitmapToViewHelper.GetBitmapFromTaskInterface {

    public static final String KEY_PREVIOUS_BG_TYPE = "KEY_PREVIOUS_BACKGROUND_TYPE";
    private static final String TAG = "SettingsFragment";
    private final static int ORIENTATION_UNKNOWN = -1;
    // Activity for result codes
    private static final int PICK_LOCK_SCREEN_PIN = 1;
    private static final int PICK_LOCK_SCREEN_PATTERN = 2;
    private static final int PICK_DEVICE_IMAGE = 3;
    private static final int PICK_APP_IMAGE = 4;


    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        // Store the background type to save it in the event it needs to be restored when being saved
        storePriorBGValue();

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // If settings believes the service is enabled and it is not (due to error of some kind), fix it.
        CheckBoxPreference checkPref = (CheckBoxPreference) findPreference(
                getString(R.string.key_toggle_lock_screen));
        if (!isServiceRunning(LockScreenService.class) && checkPref.isChecked()) {
            //Log.d(TAG, "Turning off lock screen in onResume()");
            checkPref.setChecked(false);
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        try {
            Preference lockScreenTogglePref =
                    getPreferenceScreen().findPreference(getString(R.string.key_toggle_lock_screen));
            lockScreenTogglePref.setOnPreferenceChangeListener(this);
        } catch (NullPointerException e) {
            Log.e(TAG, "Lock screen toggle preference missing from layout");
            throw e;
        }

        try {
            MyListPreference listPref = (MyListPreference)
                    findPreference(getString(R.string.key_select_lock_screen_type));
            listPref.setOnListItemClickListener(this);
        } catch (NullPointerException e) {
            Log.e(TAG, "Lock screen type selection preference missing from layout");
            throw e;
        }

        try {
            Preference speedDialBtnColorPref = getPreferenceScreen()
                    .findPreference(getString(R.string.key_select_speed_dial_button_color));
            speedDialBtnColorPref.setOnPreferenceClickListener(this);
        } catch (NullPointerException e) {
            Log.w(TAG, "Speed dial button color preference missing from layout");
        }

        try {
            Preference patternDrawColorPref = getPreferenceScreen()
                    .findPreference(getString(R.string.key_select_pattern_draw_color));
            patternDrawColorPref.setOnPreferenceClickListener(this);
        } catch (NullPointerException e) {
            Log.w(TAG, "Drawing color preference missing from layout");
        }

        try {
            Preference patternButtonColorPref = getPreferenceScreen()
                    .findPreference(getString(R.string.key_select_pattern_button_pressed_color));
            patternButtonColorPref.setOnPreferenceClickListener(this);
        } catch (NullPointerException e) {
            Log.w(TAG, "Button-pressed color preference missing from layout");
        }

        try {
            Preference fontAccessoryPref = getPreferenceScreen()
                    .findPreference(getString(R.string.key_select_accessory_fonts));
            fontAccessoryPref.setOnPreferenceClickListener(this);
        } catch (NullPointerException e) {
            Log.w(TAG, "Accessory font preference missing from layout");
        }

        try {
            Preference fontLockScreenPref = getPreferenceScreen()
                    .findPreference(getString(R.string.key_select_lock_screen_fonts));
            fontLockScreenPref.setOnPreferenceClickListener(this);
        } catch (NullPointerException e) {
            Log.w(TAG, "Accessory font preference missing from layout");
        }

        try {
            MyListPreference backgroundPref = (MyListPreference)
                    findPreference(getString(R.string.key_select_background_type));
            backgroundPref.setOnListItemClickListener(this);
        } catch (NullPointerException e) {
            Log.e(TAG, "Background type selection preference missing from layout");
            throw e;
        }

        // Update the summary
        updateBackgroundPrefSummary();
    }


    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PICK_LOCK_SCREEN_PIN:
                //Log.d(TAG, "Result from PIN activity, code = " + resultCode);
                if (resultCode == getActivity().RESULT_OK) {
                    try {
                        MyListPreference listPref = (MyListPreference) findPreference(
                                getString(R.string.key_select_lock_screen_type));
                        listPref.setValue(getString(R.string.value_lock_screen_type_keypad_pin));
                        CheckBoxPreference checkPref = (CheckBoxPreference) findPreference(
                                getString(R.string.key_toggle_lock_screen));
                        checkPref.setChecked(true);
                        // Since onChangedListener not registered yet when this call is made, need to create service
                        getActivity().startService(new Intent(getActivity(), LockScreenService.class));
                        showAlertDialogOnFirstTime();
                    } catch (ClassCastException e) {
                        Log.e(TAG, "Lock screen enabled preference of wrong type, unable to modify");
                    }
                }
                break;
            case PICK_LOCK_SCREEN_PATTERN:
                if (resultCode == getActivity().RESULT_OK) {
                    try {
                        MyListPreference listPref = (MyListPreference) findPreference(
                                getString(R.string.key_select_lock_screen_type));
                        listPref.setValue(getString(R.string.value_lock_screen_type_keypad_pattern));
                        CheckBoxPreference checkPref = (CheckBoxPreference) findPreference(
                                getString(R.string.key_toggle_lock_screen));
                        checkPref.setChecked(true);
                        getActivity().startService(new Intent(getActivity(), LockScreenService.class));
                        showAlertDialogOnFirstTime();
                    } catch (ClassCastException e) {
                        Log.e(TAG, "Lock screen enabled preference of wrong type, unable to modify");
                    }
                }
                break;
            case PICK_DEVICE_IMAGE:
                SharedPreferences prefs1 = getActivity().getSharedPreferences(
                        getString(R.string.file_background_type),
                        Context.MODE_PRIVATE);
                SharedPreferences.Editor editor1 = prefs1.edit();
                if (resultCode == getActivity().RESULT_OK) {
                    Uri selectedImage = data.getData();
                    String filePath = BitmapToViewHelper
                            .getBitmapFilePath(getActivity(), selectedImage);

                    if (filePath == null) { // unable to access filePath
                        Log.d(TAG, "Unable to obtain file path from uri: " + selectedImage.toString());
                        return;
                    }
                    editor1.putString(getString(R.string.key_select_background_device_pic), filePath);
                    int orientation = BitmapToViewHelper.getBitmapOrientation(
                            getActivity(),
                            selectedImage,
                            ORIENTATION_UNKNOWN);
                    editor1.putInt(getString(R.string.key_background_orientation), orientation);
                    //editor.remove(getString(R.string.key_background_color)); // used to flag whether to display pic or color
                    editor1.commit();
                    //Log.d(TAG, "Storing new value to previous value");
                    storePriorBGValue();
                    //Log.d(TAG, "Stored file path " + filePath + " and orientation " + orientation);
                } else {
                    //Log.d(TAG, "onActivityResult pick image received result code " + resultCode);
                    revertPriorBGValue();
                }
                //Log.d(TAG, "Updating bg summary");
                updateBackgroundPrefSummary();
                break;
            case PICK_APP_IMAGE:
                //Log.d(TAG, "onActivityResult(), PICK APP IMAGE");
                SharedPreferences prefs2 = getActivity().getSharedPreferences(
                        getString(R.string.file_background_type),
                        Context.MODE_PRIVATE);
                SharedPreferences.Editor editor2 = prefs2.edit();
                if (resultCode == getActivity().RESULT_OK) {
                    int resourceId = data.getIntExtra(AppBackgroundActivity.APP_PIC, 0);
                    if (resourceId != 0) {
                        editor2.putInt(
                                getString(R.string.key_select_background_app_content),
                                resourceId);
                        editor2.commit();
                        //Log.d(TAG, "Storing new value to previous value");
                        storePriorBGValue();
                    }
                } else {
                    // Restore the previous background type
                    //Log.d(TAG, "Reverting to prior BG value");
                    revertPriorBGValue();
                }
                updateBackgroundPrefSummary();
                break;
        }
    }

    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equals(getString(R.string.key_select_speed_dial_button_color))) {
            ColorPickerDialogFragment dialogFragment;
            int color = PreferenceManager
                    .getDefaultSharedPreferences(preference.getContext())
                    .getInt(preference.getKey(), getResources().getColor(R.color.blue_diamond));
            dialogFragment = ColorPickerDialogFragment
                    .newInstance(color, R.string.key_select_speed_dial_button_color);
            dialogFragment.show(getFragmentManager(), "fragment_color_list_dialog");
            return true;
        } else if (preference.getKey().equals(getString(R.string.key_select_pattern_draw_color))) {
            ColorPickerDialogFragment dialogFragment;
            int color = PreferenceManager
                    .getDefaultSharedPreferences(preference.getContext())
                    .getInt(preference.getKey(), getResources().getColor(R.color.green));
            dialogFragment = ColorPickerDialogFragment
                    .newInstance(color, R.string.key_select_pattern_draw_color);
            dialogFragment.show(getFragmentManager(), "fragment_color_list_dialog");
            return true;
        } else if (preference.getKey().equals(getString(R.string.key_select_pattern_button_pressed_color))) {
            ColorPickerDialogFragment dialogFragment;
            int color = PreferenceManager
                    .getDefaultSharedPreferences(preference.getContext())
                    .getInt(preference.getKey(), getResources().getColor(R.color.lava_red));
            dialogFragment = ColorPickerDialogFragment
                    .newInstance(color, R.string.key_select_pattern_button_pressed_color);
            dialogFragment.show(getFragmentManager(), "fragment_color_list_dialog");
            return true;
        } else if (preference.getKey().equals(getString(R.string.key_select_accessory_fonts))) {
            FontPickerDialogFragment dialogFragment;
            String font = PreferenceManager
                    .getDefaultSharedPreferences(preference.getContext())
                    .getString(preference.getKey(), getString(R.string.font_default));
            dialogFragment = FontPickerDialogFragment
                    .newInstance(font, R.string.key_select_accessory_fonts);
            dialogFragment.show(getFragmentManager(), "fragment_font_dialog");
        } else if (preference.getKey().equals(getString(R.string.key_select_lock_screen_fonts))) {
            FontPickerDialogFragment dialogFragment;
            String font = PreferenceManager
                    .getDefaultSharedPreferences(preference.getContext())
                    .getString(preference.getKey(), getString(R.string.font_default));
            dialogFragment = FontPickerDialogFragment
                    .newInstance(font, R.string.key_select_lock_screen_fonts);
            dialogFragment.show(getFragmentManager(), "fragment_font_dialog");
        }
        return false;
    }


    public boolean onPreferenceChange(final Preference preference, final Object newValue) {
        if (preference.getKey().equals(getString(R.string.key_toggle_lock_screen))) {
            MyListPreference pref = (MyListPreference)
                    findPreference(getString(R.string.key_select_lock_screen_type));

            // Where no lock screen type has been selected
            if (pref.getValue().equals(getString(R.string.value_lock_screen_type_none))) {
                //Set toast
                LayoutInflater inflater = getActivity().getLayoutInflater();
                View layout = inflater.inflate(
                        R.layout.toast_custom,
                        (ViewGroup) getView().findViewById(R.id.toast_custom));
                TextView text = (TextView) layout.findViewById(R.id.toast_text);
                text.setText(getString(R.string.toast_lock_screen_type_not_selected));
                Toast toast = new Toast(getActivity().getApplicationContext());
                toast.setDuration(Toast.LENGTH_LONG);
                toast.setView(layout);
                toast.show();

                // Show the lock screen selection dialog
                pref.show();
                return false;
            }
        }
        return true;
    }

    /**
     * Handles toggling of lock screen on/off by taking down the lock screen service
     *
     * @param sharedPreferences
     * @param key
     */
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        //Log.d(TAG, "onSharedPreferencesChanged called, key = " + key);
        if (key.equals(getString(R.string.key_toggle_lock_screen))) {
            CheckBoxPreference checkPref = (CheckBoxPreference) findPreference(key);
            //String text;
            if (checkPref.isChecked()) {
                Log.d(TAG, "Lock screen flagged enabled with valid unlocking mech, starting service");
                getActivity().startService(new Intent(getActivity(), LockScreenService.class));
            } else {
                Log.d(TAG, "Lock screen flagged disabled, stopping service");
                getActivity().stopService(new Intent(getActivity(), LockScreenService.class));
            }
        } else if (key.equals(getString(R.string.key_select_lock_screen_type))) {
            MyListPreference listPref = (MyListPreference) findPreference(key);
            if (listPref.getValue().equals(getString(R.string.value_lock_screen_type_none))) {
                CheckBoxPreference checkPref =
                        (CheckBoxPreference) findPreference(getString(R.string.key_toggle_lock_screen));
                if (checkPref.isChecked()) {
                    checkPref.setChecked(false);
                }
            }
        }
    }

    /**
     * Provide activated upon dialog close and sends the value of the item clicked
     *
     * @param value - contains the value of the ListPreference that was clicked
     */
    public void onListItemClick(String value, String key) {
        // Reset to no lock screen regardless until get good result in onActivityResult()
        if (key.equals(getString(R.string.key_select_lock_screen_type))) {
            try {
                CheckBoxPreference pref = (CheckBoxPreference) findPreference(
                        getString(R.string.key_toggle_lock_screen));
                pref.setChecked(false);
                MyListPreference listPref = (MyListPreference) findPreference(
                        getString(R.string.key_select_lock_screen_type));
                listPref.setValue(getString(R.string.value_lock_screen_type_none));
            } catch (ClassCastException e) {
                Log.e(TAG, "Lock screen enabled preference of wrong type, unable to modify");
            }

            if (value.equals(getString(R.string.value_lock_screen_type_keypad_pin))) {
                //Log.d(TAG, "Selected PIN activity");
                // Lock screen PIN was selected, need to go to config for that
                Intent intent = new Intent(getActivity(), KeypadPinConfigActivity.class);
                startActivityForResult(intent, PICK_LOCK_SCREEN_PIN);
            } else if (value.equals(getString(R.string.value_lock_screen_type_keypad_pattern))) {
                // Same logic as above
                Intent intent = new Intent(getActivity(), KeypadPatternConfigActivity.class);
                startActivityForResult(intent, PICK_LOCK_SCREEN_PATTERN);
            }
        } else if (key.equals(getString(R.string.key_select_background_type))) {
            MyListPreference preference = (MyListPreference) findPreference(key);

            if (value.equals(getString(R.string.value_background_type_app_content))) {
                Intent pickAppContentIntent = new Intent(getActivity(), AppBackgroundActivity.class);
                startActivityForResult(pickAppContentIntent, PICK_APP_IMAGE);
            } else if (value.equals(getString(R.string.value_background_type_color))) {
                // Launch a color picker dialog to select a color
                ColorPickerDialogFragment dialogFragment;
                SharedPreferences prefs = getActivity().getSharedPreferences(
                        getString(R.string.file_background_type),
                        Context.MODE_PRIVATE);
                int color = prefs.getInt(getString(R.string.key_select_background_color),
                        getResources().getColor(R.color.default_background_color));
                dialogFragment = ColorPickerDialogFragment
                        .newInstance(color, R.string.key_select_background_color);
                // We need feedback from the dialog here because we are using the modified list preference
                dialogFragment.setOnNoColorSelectedListener(this);
                dialogFragment.show(getFragmentManager(), "fragment_color_list_dialog");
            } else if (value.equals(getString(R.string.value_background_type_user_device))) {
                // Start intent for result
                Intent getIntent = new Intent(Intent.ACTION_GET_CONTENT);
                getIntent.setType("image/*");

                Intent pickIntent = new Intent(
                        Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                pickIntent.setType("image/*");

                Intent chooserIntent = Intent.createChooser(getIntent, "Select Image");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{pickIntent});

                startActivityForResult(chooserIntent, PICK_DEVICE_IMAGE);
            }
        }
    }

    public void onColorSelected(int color, int key) {
        if (key == R.string.key_select_speed_dial_button_color
                || key == R.string.key_select_pattern_draw_color
                || key == R.string.key_select_pattern_button_pressed_color) {
            // We can apply same logic to either of these keys
            try {
                ColorPreference pref = (ColorPreference) findPreference(getString(key));
                pref.setColor(color);
            } catch (ClassCastException e) {
                Log.e(TAG, "Wrong preference received to set color, need ColorPreference");
            } catch (NullPointerException e) {
                Log.e(TAG, "Unable to obtain ColorPreference with key");
            }

        } else if (key == R.string.key_select_background_color) {
            SharedPreferences prefs = getActivity().getSharedPreferences(
                    getString(R.string.file_background_type),
                    Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(getString(R.string.key_select_background_color), color);
            editor.commit();
            //Log.d(TAG, "Storing new value to prior value");
            storePriorBGValue();
            //Log.d(TAG, "Updating bg summary");
            updateBackgroundPrefSummary();
        }
    }

    public void onNoColorSelected(int key) {

        if (key == R.string.key_select_background_color) {
            Log.d(TAG, "onNoColorSelected() called");
            // Revert to the prior saved value
            revertPriorBGValue();
            updateBackgroundPrefSummary();
        }

    }

    public void onFontSelected(String font, int key) {
        if (key == R.string.key_select_accessory_fonts
                || key == R.string.key_select_lock_screen_fonts) {
            try {
                Preference pref = findPreference(getString(key));
                pref.setSummary(font);
                SharedPreferences.Editor edit = pref.getEditor();
                edit.putString(getString(key), font);
                edit.commit();
            } catch (NullPointerException e) {
                Log.e(TAG, "Unable to obtain FontPreference with key");
            }
        }
    }

    public void getBitmapFromTask(Bitmap bitmap) {

    }


    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager =
                (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void updatePreferenceSummary(String key, String summary) {
        try {
            Preference pref = findPreference(key);
            pref.setSummary(summary);
        } catch (Exception e) {
            Log.e(TAG, "Unable to update preference summary; key not tethered to settings preference");
        }
    }

    /**
     * Returns the color name corresponding to an integer as set forth in the xml.  Returns empty
     * string if color not found.
     *
     * @param color
     * @return
     */
    private String getColorName(int color) {
        String[] colorNames = getResources().getStringArray(R.array.color_name_list);
        TypedArray colorValues = getResources().obtainTypedArray(R.array.color_value_list);
        int length = (colorNames.length < colorValues.length()) ?
                colorNames.length : colorValues.length();
        for (int i = 0; i < length; i++) {
            if (colorValues.getColor(i, -1) == color) {
                return colorNames[i];
            }
        }
        return "";
    }

    private void updateBackgroundPrefSummary() {
        MyListPreference bgTypePref =
                (MyListPreference) findPreference(getString(R.string.key_select_background_type));
        SharedPreferences prefs = getActivity().getSharedPreferences(
                getString(R.string.file_background_type),
                Context.MODE_PRIVATE);
        String bgType = bgTypePref.getValue();
        //Log.d(TAG, "UPDATE BG Pref: background type is " + bgType);

        if (bgType.equals(getString(R.string.value_background_type_app_content))) {
            int resourceId = prefs.getInt(getString(R.string.key_select_background_app_content), 0);
            TypedArray appPics = getResources().obtainTypedArray(R.array.app_pics);
            String[] appPicsNames = getResources().getStringArray(R.array.app_pics_names);
            String title;
            if (resourceId != AppBackgroundActivity.RANDOM_PIC) {
                title = "unknown";
                for (int i = 0; i < appPics.length(); i++) {
                    if (appPics.getResourceId(i, 0) == resourceId) {
                        title = appPicsNames[i];
                        break;
                    }
                }
            } else {
                title = "random";
            }
            bgTypePref.setSummary(
                    getString(R.string.pref_summary_prefix_app_content_background) + " " + title
            );
        } else if (bgType.equals(getString(R.string.value_background_type_user_device))) {
            String filePath = prefs
                    .getString(getString(R.string.key_select_background_device_pic), "");
            bgTypePref.setSummary(getString(R.string.pref_summary_prefix_user_device_background)
                    + " " + filePath.substring(filePath.lastIndexOf("/") + 1));

        } else if (bgType.equals(getString(R.string.value_background_type_color))) {

            int color = prefs.getInt(getString(R.string.key_select_background_color),
                    getResources().getColor(R.color.default_background_color));
            bgTypePref.setSummary(getString(R.string.pref_summary_prefix_color_background) + " "
                    + getColorName(color));
        }

    }

    private void storePriorBGValue() {
        SharedPreferences prefs = getActivity().getSharedPreferences(
                getString(R.string.file_background_type),
                Context.MODE_PRIVATE);
        MyListPreference bgPref =
                (MyListPreference) findPreference(getString(R.string.key_select_background_type));
        String bgValue = bgPref.getValue();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_PREVIOUS_BG_TYPE, bgValue);
        editor.commit();
        //Log.d(TAG, "STORE PREV VALUE: background type is " + bgValue);
    }

    private void revertPriorBGValue() {
        SharedPreferences prefs = getActivity().getSharedPreferences(
                getString(R.string.file_background_type),
                Context.MODE_PRIVATE);
        String value = prefs.getString(
                KEY_PREVIOUS_BG_TYPE,
                getString(R.string.value_background_type_app_content));
        //Log.d(TAG, "REVERTING PREV VALUE: background type was " + value);

        /*
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(getString(R.string.key_select_background_type), value);
        editor.commit();*/
        // Change the value through the actual preference
        MyListPreference bgPref =
                (MyListPreference) findPreference(getString(R.string.key_select_background_type));
        String[] values = getResources().getStringArray(R.array.pref_values_select_background_type);
        // Can probably just use setValue now that we are getting the right prev value, but this works so don't fix it
        for (int i = 0; i < values.length; i++) {
            //Log.d(TAG, "values[" + i + "] is " + values[i]);
            if (values[i].equals(value)) {
                //Log.d(TAG, "setValueIndex for " + values[i]);
                bgPref.setValueIndex(i);
                break;
            }
        }
    }

    /**
     * Shows an alert dialog on the user's first time using the app, instructing user to
     * disable the device's native lock screen
     */
    private void showAlertDialogOnFirstTime() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean isFirstTime = prefs.getBoolean(getString(R.string.key_is_first_time), true);
        if (isFirstTime) {
            // Be sure to store the value so it doesn't come up again!
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(getString(R.string.key_is_first_time), false);
            editor.commit();

            // Now build an alert dialog to display
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());

            dialogBuilder.setTitle(getString(R.string.alert_dialog_title_first_time));
            dialogBuilder
                    .setMessage(getString(R.string.alert_dialog_message_first_time))
                    .setCancelable(false)
                    .setNegativeButton(getString(R.string.alert_dialog_button_text_first_time),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            });
            AlertDialog alertDialog = dialogBuilder.create();
            alertDialog.show();
        }

    }



}
