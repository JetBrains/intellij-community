// "Remove 'final' modifier" "true"

class A() {
    @Deprecated("") // wd
    final<caret> constructor(i: Int): this()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase