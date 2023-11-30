// "Remove 'operator' modifier" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ProhibitOperatorMod

object A {
    operator<caret> fun mod(x: Int) {}
}
/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase