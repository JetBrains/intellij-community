// "Remove 'inner' modifier" "true"
interface A {
    inne<caret>r class B
}

/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase