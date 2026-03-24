// "Change function signature to 'fun f(a: Int, x: T)'" "true"
// K2_ERROR: 'f' overrides nothing. Potential signatures for overriding:<br>fun f(a: Int, b: T): Unit
// K2_ERROR: Class 'B' is not abstract and does not implement abstract member:<br>fun f(a: Int, b: R): Unit
interface A<R> {
    fun f(a: Int, b: R)
}

class B<T> : A<T> {
    <caret>override fun f(x: T) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix