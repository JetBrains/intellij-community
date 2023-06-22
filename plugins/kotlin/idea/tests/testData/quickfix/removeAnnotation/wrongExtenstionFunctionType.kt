// "Remove inapplicable @ExtensionFunctionType annotation" "true"
// IGNORE_FIR
// WITH_STDLIB
fun bar(f: <caret>@ExtensionFunctionType () -> Int): Int = TODO()
