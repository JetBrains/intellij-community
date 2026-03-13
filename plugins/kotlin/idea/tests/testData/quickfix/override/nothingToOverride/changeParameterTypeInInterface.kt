// "Change function signature to 'fun f(a: Int)'" "true"
// K2_ERROR: 'f' overrides nothing. Potential signatures for overriding:<br>fun f(a: Int): Unit
interface A {
    fun f(a: Int)
}

interface B : A {
    <caret>override fun f(a: String)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix