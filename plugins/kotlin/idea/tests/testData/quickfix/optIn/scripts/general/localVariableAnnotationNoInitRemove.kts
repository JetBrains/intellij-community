// "Remove annotation" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME

@RequiresOptIn
@Target(AnnotationTarget.LOCAL_VARIABLE)
annotation class SomeOptInAnnotation

fun foo() {
    <caret>@SomeOptInAnnotation
    var x: Int
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix