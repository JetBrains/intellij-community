// "Remove inapplicable @ExtensionFunctionType annotation" "true"
// WITH_STDLIB
// K2_ERROR: '@ExtensionFunctionType' is prohibited on a function type without parameters or on a non-function type.
fun bar(f: <caret>@ExtensionFunctionType () -> Int): Int = TODO()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix