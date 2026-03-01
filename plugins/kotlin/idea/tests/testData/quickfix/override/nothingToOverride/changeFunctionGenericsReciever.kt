// "Change function signature to 'fun Int.foo()'" "true"
abstract class C<T> {
    abstract fun T.foo()
}

class B : C<Int>() {
    <caret>override fun String.foo() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix