// "Make 'i' not abstract" "true"
// K2_ERROR: ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS
class B {
    <caret>abstract val i: Int = 0
        get() = field
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase