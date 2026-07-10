// "Make 'foo' not open" "true"
// K2_ERROR: DECLARATION_CANT_BE_INLINED
open class A {
    inli<caret>ne open fun foo() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeModifiersFix