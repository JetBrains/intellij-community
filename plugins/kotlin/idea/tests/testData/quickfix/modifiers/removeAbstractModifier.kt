// "Make 'abstract()' not abstract" "true"
// K2_ERROR: Modifier 'abstract' is not applicable to 'constructor'.
// K2_ERROR: Use the 'constructor' keyword after the modifiers of the primary constructor.

class A <caret>abstract() {

}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase