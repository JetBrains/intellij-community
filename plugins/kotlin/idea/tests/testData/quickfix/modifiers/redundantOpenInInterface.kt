// "Remove redundant 'open' modifier" "true"

interface My {
    <caret>open fun foo()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase