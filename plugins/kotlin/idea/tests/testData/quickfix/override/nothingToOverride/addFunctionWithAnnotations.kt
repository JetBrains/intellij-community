// "Add 'abstract fun foo()' to 'I'" "true"
annotation class A(vararg val names: String)
annotation class B(val i: Int)

interface I

class C : I {
    @A("x", "y")
    @B(1)
    <caret>override fun foo() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionToSupertypeFix

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddFunctionToSupertypeFix