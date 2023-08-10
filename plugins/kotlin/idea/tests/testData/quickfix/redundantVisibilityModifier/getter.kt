// "Remove redundant 'public' modifier" "true"
val a <caret>public get() = 0
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase