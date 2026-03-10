// "Make 'Foo' 'abstract'" "true"
// K2_ERROR: Class 'Foo' is not abstract and does not implement abstract base class member:<br>val i: Int

abstract class Bar {
    abstract val i: Int
}

class <caret>Foo : Bar() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp