// "Make 'i' not abstract" "true"
// K2_ERROR: ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS
class A() {
    <caret>abstract var i : Int = 0
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase