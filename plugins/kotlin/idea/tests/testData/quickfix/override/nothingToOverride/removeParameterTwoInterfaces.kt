// "Change function signature to 'fun f()'" "true"
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// K2_ERROR: NOTHING_TO_OVERRIDE
interface A {
    fun f()
}
interface B {
    fun f()
}

class C : A, B {
    <caret>override fun f(a: String) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix