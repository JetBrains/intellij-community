// "Change function signature to 'fun f(y: Int, x: String)'" "true"
// K2_ERROR: NOTHING_TO_OVERRIDE
open class A {
    open fun f(a: Int, b: String) {}
}

class B : A(){
    <caret>override fun f(x: String, y: Int) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix