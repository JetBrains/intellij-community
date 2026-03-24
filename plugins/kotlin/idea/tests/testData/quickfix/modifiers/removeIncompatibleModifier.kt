// "Remove 'public' modifier" "true"
// K2_ERROR: Modifier 'private' is incompatible with 'public'.
// K2_ERROR: Modifier 'public' is incompatible with 'private'.
class Foo {
    public<caret> private fun bar() { }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase