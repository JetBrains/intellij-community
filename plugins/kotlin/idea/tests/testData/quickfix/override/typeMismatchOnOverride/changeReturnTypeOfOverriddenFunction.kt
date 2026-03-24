// "Change return type of base function 'A.foo' to 'Long'" "true"
// K2_ERROR: Return type of 'fun foo(): Long' is not a subtype of the return type of the overridden member 'fun foo(): Int' defined in 'A'.
interface A {
    fun foo(): Int
}

interface B {
    fun foo(): Number
}

interface C : A, B {
    override fun foo(): Long<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForOverridden
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix