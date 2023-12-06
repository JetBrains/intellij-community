// "Convert to 'isArrayOf' call" "true"
// WITH_STDLIB
fun test(a: Any) {
    if (a !is <caret>Array<String>) {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToIsArrayOfCallFix