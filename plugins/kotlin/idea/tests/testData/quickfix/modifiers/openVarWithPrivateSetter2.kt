// "Remove 'private' modifier" "true"
// K2_ERROR: Private setters for open properties are prohibited.
open class My {
    open var foo = 42
        <caret>private set
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase