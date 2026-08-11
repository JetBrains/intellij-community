// "Make 'Foo' 'abstract'" "true"
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED

class <caret>Foo : List<String> {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp