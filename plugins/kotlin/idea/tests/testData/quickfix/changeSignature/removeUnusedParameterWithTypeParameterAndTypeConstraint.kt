// "Remove parameter 'x'" "true"
fun <X> foo(<caret>x: X) where X : Number {}

fun test() {
    foo(1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix