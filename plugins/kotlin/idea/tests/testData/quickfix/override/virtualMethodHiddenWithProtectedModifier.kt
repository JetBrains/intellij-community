// "Add 'override' modifier" "true"
interface Foo {
    fun bar()
}

class Bar : Foo {
    protected fun <caret>bar() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix