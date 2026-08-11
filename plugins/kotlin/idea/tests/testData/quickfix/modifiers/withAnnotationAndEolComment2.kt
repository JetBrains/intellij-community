// "Remove 'final' modifier" "true"
// K2_ERROR: WRONG_MODIFIER_TARGET

class A() {
    @Deprecated("") // wd
    final<caret> constructor(i: Int): this()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase