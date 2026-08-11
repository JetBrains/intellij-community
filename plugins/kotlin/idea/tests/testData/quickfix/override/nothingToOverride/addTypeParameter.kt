// "Change function signature to 'fun <T : Number> f(a: T)'" "true"
// K2_ERROR: NOTHING_TO_OVERRIDE
open class A {
    open fun <T : Number> f(a: T) {}
}

class B : A() {
    <caret>override fun f(a: Int) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix