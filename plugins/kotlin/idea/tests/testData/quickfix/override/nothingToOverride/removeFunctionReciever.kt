// "Change function signature to 'fun x(s: String)'" "true"
// K2_ERROR: 'x' overrides nothing. Potential signatures for overriding:<br>fun x(s: String): Unit
open class A {
    open fun x(s: String) {}
}

class B : A() {
    <caret>override fun String.x() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix