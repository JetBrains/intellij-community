// "Change return type to 'S'" "true"
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH_ON_OVERRIDE
// K2_ERROR: RETURN_TYPE_MISMATCH_ON_OVERRIDE
open class S {}
open class T : S() {}

abstract class A {
    abstract fun foo() : S;
}

interface X {
    fun foo() : T;
}

abstract class B : A(), X {
    override abstract fun foo(): Int<caret>
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$OnType
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix