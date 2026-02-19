// "Change function signature to 'fun f()'" "true"
open class A {
    open fun f() {}
}

open class B : A() {
    open override fun f() {}
}

class C : B() {
    <caret>override fun f(a : Int) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix