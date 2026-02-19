// "Change function signature to 'fun f(x: Int, t: String, z: Double)'" "true"
open class A {
    open fun f(x: Int, y: String, z: Double) {}
}

class B : A(){
    <caret>override fun f(z: String, x: String, t: String) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix