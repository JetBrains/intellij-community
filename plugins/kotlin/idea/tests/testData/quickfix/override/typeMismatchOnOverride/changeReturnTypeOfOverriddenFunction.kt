// "Change return type of base function 'A.foo' to 'Long'" "true"
// K2_ERROR: RETURN_TYPE_MISMATCH_ON_OVERRIDE
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