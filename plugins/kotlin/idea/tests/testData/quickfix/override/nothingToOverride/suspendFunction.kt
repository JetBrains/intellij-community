// "Change function signature to 'suspend fun foo(x: String)'" "true"
// K2_ERROR: 'foo' overrides nothing. Potential signatures for overriding:<br>suspend fun foo(x: String): Unit
// K2_ERROR: Class 'Bar' is not abstract and does not implement abstract member:<br>suspend fun foo(x: String): Unit
interface Foo {
    suspend fun foo(x: String)
}

class Bar : Foo {
    <caret>override fun foo() {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix