// "Remove annotation" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME
// K2_ERROR: Opt-in requirement marker annotation cannot be used on parameter.

@RequiresOptIn
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class SomeOptInAnnotation

class Foo(<caret>@SomeOptInAnnotation val value: Int) {
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix