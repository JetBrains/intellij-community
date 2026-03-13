// "Remove 'private' modifier" "true"
// K2_ERROR: Private setters for abstract properties are prohibited.
abstract class My {
    abstract var foo: Int
        <caret>private set
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase