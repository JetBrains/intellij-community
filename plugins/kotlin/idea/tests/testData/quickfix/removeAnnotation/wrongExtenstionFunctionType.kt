// "Remove inapplicable @ExtensionFunctionType annotation" "true"
// WITH_STDLIB
// K2_ERROR: WRONG_EXTENSION_FUNCTION_TYPE
fun bar(f: <caret>@ExtensionFunctionType () -> Int): Int = TODO()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix