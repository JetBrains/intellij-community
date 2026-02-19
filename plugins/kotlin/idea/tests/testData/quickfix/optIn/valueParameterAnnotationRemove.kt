// "Remove annotation" "true"
// WITH_STDLIB

@RequiresOptIn
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class SomeOptInAnnotation

class Foo(<caret>@SomeOptInAnnotation val value: Int) {
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix