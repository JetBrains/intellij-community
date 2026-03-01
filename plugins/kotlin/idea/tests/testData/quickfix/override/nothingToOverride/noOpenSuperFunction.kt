// "class org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix" "false"
// ERROR: 'f' overrides nothing
// K2_AFTER_ERROR: 'f' overrides nothing.
open class A {
    open fun foo() {}
    fun f(a: Int) {}
}

class B : A(){
    <caret>override fun f(a: String) {}
}
