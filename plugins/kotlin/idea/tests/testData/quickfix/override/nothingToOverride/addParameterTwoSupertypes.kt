// "Change function signature to 'fun f(a: Int)'" "true"
open class A {
    open fun f(a: Int) {}
}

open class B : A() {
}

class C : B() {
    <caret>override fun f() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix