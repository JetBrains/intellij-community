// "Create local variable 'defaultPreferencesName'" "false"
// ERROR: Unresolved reference: defaultPreferencesName
// ERROR: Default value of annotation parameter must be a compile-time constant
// K2_AFTER_ERROR: Unresolved reference 'defaultPreferencesName'.

class AppModule {
    val defaultPreferencesName  = "defaultPreferences"

    annotation class Preferences(val type: String = <caret>defaultPreferencesName)
}