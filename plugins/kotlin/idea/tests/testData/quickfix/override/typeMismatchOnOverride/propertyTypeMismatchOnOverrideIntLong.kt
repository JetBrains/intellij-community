// "Change type to 'Int'" "true"
abstract class A {
    abstract var x : Int
}

abstract class B : A() {
    override abstract var x: Long<caret>
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix$OnType
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix