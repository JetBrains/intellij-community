// "Convert to 'isArrayOf' call" "true"
// WITH_STDLIB
// K2_ERROR: CANNOT_CHECK_FOR_ERASED
fun test(a: Any) {
    if (a is <caret>Array<String>) {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToIsArrayOfCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToIsArrayOfCallFix