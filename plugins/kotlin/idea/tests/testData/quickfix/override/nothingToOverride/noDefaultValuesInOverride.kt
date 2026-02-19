// "Change function signature to 'fun foo(a: Int, b: String): Any?'" "true"
interface  A {
    public fun foo(a: Int = 1, b: String = "str"): Any?
}

class B : A {
    public override<caret> fun foo(a: Int): Any? = null
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeMemberFunctionSignatureFixFactory$ChangeMemberFunctionSignatureFix