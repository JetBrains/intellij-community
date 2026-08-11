// "Remove 'final' modifier" "true"
// K2_ERROR: WRONG_MODIFIER_TARGET

class A @Deprecated("") // ds
final<caret> constructor() {

}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase