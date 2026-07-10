// "Change function signature to 'fun x(s: String)'" "true"
// K2_ERROR: NOTHING_TO_OVERRIDE
open class A {
    open fun x(s: String) {}
}

class B : A() {
    <caret>override fun String.x() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix