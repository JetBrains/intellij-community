// "Change function signature to 'context(_: Int) fun f()'" "true"
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// K2_ERROR: NOTHING_TO_OVERRIDE

annotation class X

interface A {
    @X
    context(_: Int)
    fun f()
}

class B: A {
    @X
    <caret>override fun f() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix