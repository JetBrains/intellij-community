// "Add 'override' modifier" "true"
// K2_ERROR: VIRTUAL_MEMBER_HIDDEN
interface Foo {
    fun bar()
}

class Bar : Foo {
    protected fun <caret>bar() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix