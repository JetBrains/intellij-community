// "Change function signature to 'fun <T> f(a: (T & Any).() -> Unit)'" "true"
open class A {
    open fun <T> f(a: (T & Any).() -> Unit) {}
}

class B : A() {
    <caret>override fun f(a: String) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix