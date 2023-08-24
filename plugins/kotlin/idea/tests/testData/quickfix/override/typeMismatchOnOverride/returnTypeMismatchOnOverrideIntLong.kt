// "Change return type to 'Int'" "true"
abstract class A {
    abstract fun foo() : Int;
}

abstract class B : A() {
    abstract override fun foo(): Long<caret>
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$OnType
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix