// "Make 'Foo' 'abstract'" "true"

class <caret>Foo : List<String> {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix