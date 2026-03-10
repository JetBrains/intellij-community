// "Remove 'fun' modifier" "true"
// K2_ERROR: Functional interface must have exactly one abstract function.
<caret>fun interface WrongFunFace
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase