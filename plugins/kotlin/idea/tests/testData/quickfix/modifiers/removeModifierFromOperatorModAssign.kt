// "Remove 'operator' modifier" "true"

object A {
    operator<caret> fun modAssign(x: Int) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase