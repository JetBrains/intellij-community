// "Make 'foo' 'abstract'" "true"
abstract class B() {
    open fun <caret>foo()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix