// "Make 'B' 'abstract'" "true"
// K2_ERROR: Class 'B' is not abstract and does not implement abstract base class member:<br>fun foo(): Unit
abstract class A {
    abstract fun foo()
}

<caret>class B : A() {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp