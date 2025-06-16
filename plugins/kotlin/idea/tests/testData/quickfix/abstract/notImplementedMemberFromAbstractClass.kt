// "Make 'B' 'abstract'" "true"
abstract class A {
    abstract fun foo()
}

<caret>class B : A() {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp