// "Make not-nullable" "true"
// K2_ERROR: INAPPLICABLE_LATEINIT_MODIFIER

class A() {
    <caret>lateinit var foo: String?
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNullableFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNullableFix