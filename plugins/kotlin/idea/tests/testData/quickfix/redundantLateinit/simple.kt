// "Remove 'lateinit' modifier" "true"
// K2_ERROR: INAPPLICABLE_LATEINIT_MODIFIER

class Test {
    private <caret>lateinit var foo: String = ""
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase