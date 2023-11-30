// "Remove 'override' modifier" "true"
class A() {
    <caret>override fun foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase