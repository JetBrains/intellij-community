// "Remove 'suspend' modifier" "true"

suspend inline fun foo(c: <caret>suspend () -> Unit) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase