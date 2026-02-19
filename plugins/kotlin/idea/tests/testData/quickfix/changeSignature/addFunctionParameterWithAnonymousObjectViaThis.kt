// "Add parameter to function 'handle'" "true"
interface Callback {
    fun onEvent()
}

fun handle() {}

fun test() {
    object : Callback {
        override fun onEvent() {
            handle(<caret>this)
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix
