// "Change function signature to 'suspend fun foo(x: String)'" "true"
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// K2_ERROR: NOTHING_TO_OVERRIDE
interface Foo {
    suspend fun foo(x: String)
}

class Bar : Foo {
    <caret>override fun foo() {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix