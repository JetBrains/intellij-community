// "Add 'val' to parameter 'x'" "true"
/* IGNORE_K2 */
annotation class A(<caret>x: Int)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddValVarToConstructorParameterAction$QuickFix