// "Remove parameter 'x'" "true"
fun <X> foo(<caret>x: X) {}

fun test() {
    foo(x = 1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix