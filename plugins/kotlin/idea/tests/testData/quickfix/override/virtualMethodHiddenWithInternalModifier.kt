// "Add 'override' modifier" "true"
interface Foo {
    fun bar()
}

class Bar : Foo {
    internal fun <caret>bar() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix