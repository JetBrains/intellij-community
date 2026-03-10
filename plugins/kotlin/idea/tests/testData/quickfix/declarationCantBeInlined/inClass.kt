// "Make 'foo' not open" "true"
// K2_ERROR: 'inline' modifier on virtual members is prohibited. Only private or final members can be inlined.
open class A {
    inli<caret>ne open fun foo() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeModifiersFix