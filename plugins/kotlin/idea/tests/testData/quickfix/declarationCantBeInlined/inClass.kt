// "Make 'foo' not open" "true"
open class A {
    inli<caret>ne open fun foo() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFix