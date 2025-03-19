// "Remove parameter 'x'" "true"

fun foo() {
    X("")
}
class X constructor(<caret>x: String)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix