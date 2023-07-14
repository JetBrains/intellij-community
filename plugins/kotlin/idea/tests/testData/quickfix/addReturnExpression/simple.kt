// "Add 'return' expression" "true"
// WITH_STDLIB
fun test(): Boolean {
    foo()
}<caret>

fun foo() {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnExpressionFix