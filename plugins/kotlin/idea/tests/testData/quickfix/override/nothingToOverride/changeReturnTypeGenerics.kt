// "Change function signature to 'fun f(t: Int): Int'" "true"
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// K2_AFTER_ERROR: Missing return statement.
abstract class A<T> {
    abstract fun f(t: T): T
}

class B : A<Int>() {
    <caret>override fun f() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix