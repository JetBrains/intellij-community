// "Convert to 'isArrayOf' call" "true"
// WITH_STDLIB
fun test(a: Array<*>?) {
    if (a !is <caret>Array<String>) {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToIsArrayOfCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToIsArrayOfCallFix