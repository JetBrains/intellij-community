// "Remove 'final' modifier" "true"
// K2_ERROR: Modifier 'final' is not applicable to 'constructor'.

class A @Deprecated("") // ds
final<caret> constructor() {

}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase