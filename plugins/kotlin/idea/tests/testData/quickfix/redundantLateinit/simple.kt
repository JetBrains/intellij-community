// "Remove 'lateinit' modifier" "true"

class Test {
    private <caret>lateinit var foo: String = ""
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase