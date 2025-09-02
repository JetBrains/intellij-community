// "Add 'override' modifier" "true"
open class A() {
    open fun foo() {}
}

class B() : A() {
    fun <caret>foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix