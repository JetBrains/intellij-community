// "Make 'Foo' 'abstract'" "true"

abstract class Bar {
    abstract val i: Int
}

class <caret>Foo : Bar() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp