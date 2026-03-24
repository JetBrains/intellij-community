// "Change function signature to 'fun Int.foo(a: String)'" "true"
// K2_ERROR: 'foo' overrides nothing. Potential signatures for overriding:<br>fun Int.foo(a: String): Unit
// K2_ERROR: Class 'B' is not abstract and does not implement abstract base class member:<br>fun Int.foo(a: String): Unit
abstract class C {
    abstract fun Int.foo(a: String)
}

class B : C() {
    <caret>override fun foo(a: Int) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix