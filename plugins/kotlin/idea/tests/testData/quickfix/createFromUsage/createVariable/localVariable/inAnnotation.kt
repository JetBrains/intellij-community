// "Create local variable 'defaultPreferencesName'" "false"
// ERROR: Unresolved reference: defaultPreferencesName
// ERROR: Default value of annotation parameter must be a compile-time constant
// K2_AFTER_ERROR: Default value of annotation parameter must be a compile-time constant.
// K2_AFTER_ERROR: Outer class 'class AppModule : Any' of non-inner class cannot be used as receiver.

class AppModule {
    val defaultPreferencesName  = "defaultPreferences"

    annotation class Preferences(val type: String = <caret>defaultPreferencesName)
}