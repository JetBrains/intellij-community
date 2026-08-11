// "Change 'object' to 'class'" "false"
// K2_AFTER_ERROR: CONSTRUCTOR_IN_OBJECT
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: CONSTRUCTOR_IN_OBJECT
// K2_ERROR: UNRESOLVED_REFERENCE


class Foo(val s: String) {
    companion object {
        <caret>constructor() : this("")
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeObjectToClassFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeObjectToClassFix