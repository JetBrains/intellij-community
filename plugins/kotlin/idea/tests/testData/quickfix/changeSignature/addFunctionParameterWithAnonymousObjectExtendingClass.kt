// "Add parameter to function 'execute'" "true"
open class Base {
    open fun action() {}
}

fun execute() {}

fun test() {
    execute(<caret>object : Base() {
        override fun action() {}
    })
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix
