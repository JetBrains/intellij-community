// "Remove parameter 'x'" "true"
fun <X> foo(<caret>x: X, x2: X) {}

fun test() {
    foo(x = 1, x2 = 2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix