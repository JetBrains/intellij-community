// "Add 'override' modifier" "true"
// K2_ERROR: VIRTUAL_MEMBER_HIDDEN
open class Foo {
    internal open fun bar() {}
}

class Bar : Foo() {
    public fun <caret>bar() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix