// "Remove redundant 'open' modifier" "true"

interface My {
    <caret>open fun foo()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase