// "Change function signature…" "true"
// ERROR: Class 'B' is not abstract and does not implement abstract member public abstract fun f(a: String): Unit defined in A
// K2_AFTER_ERROR: Class 'B' is not abstract and does not implement abstract member:<br>fun f(a: String): Unit
interface A {
    fun f(a: Int)
    fun f(a: String)
}

class B : A {
    <caret>override fun f() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChooseSuperSignatureFix