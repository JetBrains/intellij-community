// "Make 'Foo' not open" "true"
// K2_ERROR: Modifier 'open' is not applicable to 'standalone object'.
<caret>open object Foo {
    fun a(): Int = 1
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase