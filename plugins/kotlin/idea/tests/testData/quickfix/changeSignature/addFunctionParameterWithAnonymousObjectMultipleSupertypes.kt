// "Add parameter to function 'process'" "true"
interface A {
    fun foo()
}

interface B {
    fun bar()
}

fun process() {}

fun test() {
    process(<caret>object : A, B {
        override fun foo() {}
        override fun bar() {}
    })
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix
