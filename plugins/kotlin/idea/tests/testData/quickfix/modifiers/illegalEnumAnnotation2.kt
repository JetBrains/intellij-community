// "Remove 'enum' modifier" "true"
// K2_ERROR: Modifier 'enum' is not applicable to 'member function'.
class A() {
    <caret>enum fun foo() {}
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase