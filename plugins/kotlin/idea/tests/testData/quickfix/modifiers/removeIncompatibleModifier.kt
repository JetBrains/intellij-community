// "Remove 'public' modifier" "true"
// K2_ERROR: INCOMPATIBLE_MODIFIERS
// K2_ERROR: INCOMPATIBLE_MODIFIERS
class Foo {
    public<caret> private fun bar() { }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase