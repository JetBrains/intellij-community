// "Create local variable 'defaultPreferencesName'" "false"
// ERROR: Unresolved reference: defaultPreferencesName
// ERROR: Default value of annotation parameter must be a compile-time constant
// K2_AFTER_ERROR: ANNOTATION_PARAMETER_DEFAULT_VALUE_MUST_BE_CONSTANT
// K2_AFTER_ERROR: INACCESSIBLE_OUTER_CLASS_RECEIVER
// K2_ERROR: ANNOTATION_PARAMETER_DEFAULT_VALUE_MUST_BE_CONSTANT
// K2_ERROR: INACCESSIBLE_OUTER_CLASS_RECEIVER

class AppModule {
    val defaultPreferencesName  = "defaultPreferences"

    annotation class Preferences(val type: String = <caret>defaultPreferencesName)
}