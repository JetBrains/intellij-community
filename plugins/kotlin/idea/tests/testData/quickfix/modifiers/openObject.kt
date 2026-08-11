// "Make 'Foo' not open" "true"
// K2_ERROR: WRONG_MODIFIER_TARGET
<caret>open object Foo {
    fun a(): Int = 1
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase