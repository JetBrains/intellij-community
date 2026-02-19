// "Change function signature to 'fun f(a: Int): Int'" "true"
open class A {
    open fun f(a: Int): Int {
        return 0
    }
}

class B : A(){
    <caret>override fun f(a: String): Int = 7
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix