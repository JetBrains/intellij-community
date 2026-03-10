// "Change function signature to 'fun f(y: S, x: List<Set<R>>)'" "true"
// K2_ERROR: 'f' overrides nothing. Potential signatures for overriding:<br>fun f(a: S, b: List<Set<R>>): Unit
// K2_ERROR: Class 'B' is not abstract and does not implement abstract member:<br>fun f(a: Q, b: List<Set<P>>): Unit
interface A<P,Q> {
    fun f(a: Q, b: List<Set<P>>)
}

class B<R,S> : A<R,S> {
    <caret>override fun f(x: List<Set<R>>, y: S) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix