// "Add 'override' modifier" "true"
// K2_ERROR: 'bar' hides member of supertype 'Foo' and needs an 'override' modifier.
interface Foo {
    fun bar()
}

class Bar : Foo {
    private fun <caret>bar() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix