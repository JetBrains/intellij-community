// "Add 'override' modifier" "true"
open class Foo {
    internal open fun bar() {}
}

class Bar : Foo() {
    public fun <caret>bar() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix