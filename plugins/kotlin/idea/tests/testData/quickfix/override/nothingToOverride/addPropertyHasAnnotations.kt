// "Add 'abstract val bar: Int' to 'I'" "true"
annotation class A(vararg val names: String)
annotation class B(val i: Int)

interface I {
}

class C : I {
    @A("x", "y")
    @B(1)
    <caret>override val bar = 1
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddPropertyToSupertypeFix

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddPropertyToSupertypeFix