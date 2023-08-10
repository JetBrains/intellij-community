// "Remove parameter 'x'" "true"
class Bar<X> {
    fun foo(<caret>x: X) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix