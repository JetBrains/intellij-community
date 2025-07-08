// "Change return type to 'S'" "true"
// K2_AFTER_ERROR: Return type of 'foo' is not a subtype of the return type of the overridden member 'fun foo(): T' defined in 'X'.
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