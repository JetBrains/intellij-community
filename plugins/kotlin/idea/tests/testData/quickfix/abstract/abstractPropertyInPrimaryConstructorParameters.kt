// "Make 'i' not abstract" "true"
// K2_ERROR: Property in primary constructor cannot be declared as abstract.
class A(<caret>abstract val i: Int) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase