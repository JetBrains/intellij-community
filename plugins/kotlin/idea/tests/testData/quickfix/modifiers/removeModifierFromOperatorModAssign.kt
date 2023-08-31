// "Remove 'operator' modifier" "true"

object A {
    operator<caret> fun modAssign(x: Int) {}
}
/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase