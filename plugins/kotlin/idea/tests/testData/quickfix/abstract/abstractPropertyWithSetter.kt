// "Make 'j' not abstract" "true"
class B {
    abstract<caret> var j: Int = 0
        set(v: Int) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase