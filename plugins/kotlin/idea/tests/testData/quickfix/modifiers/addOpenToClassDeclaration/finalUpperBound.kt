// "Make 'A' 'open'" "true"
final class A {}
class B<T : A<caret>> {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp