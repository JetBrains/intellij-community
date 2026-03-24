// "Remove 'protected' modifier" "true"
// K2_ERROR: Modifier 'protected' is not applicable to 'top level property with backing field'.
package test

<caret>protected val a: Int = 0


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase