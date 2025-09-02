// "Change 'object' to 'class'" "false"
// K2_AFTER_ERROR: Objects cannot have constructors.
// K2_AFTER_ERROR: Unresolved reference 'Companion'.
// IGNORE_K1

class Foo(val s: String) {
    companion object {
        <caret>constructor() : this("")
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeObjectToClassFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeObjectToClassFix