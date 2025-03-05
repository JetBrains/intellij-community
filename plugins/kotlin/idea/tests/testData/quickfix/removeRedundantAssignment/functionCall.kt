// "Remove redundant assignment" "true"
fun foo() = 1

fun test() {
    var i: Int
    <caret>i = foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedValueFix
// IGNORE_K2
// TODO: Add support for checking side effects in K2