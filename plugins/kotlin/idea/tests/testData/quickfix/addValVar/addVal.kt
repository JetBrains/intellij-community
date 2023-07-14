// "Add 'val' or 'var' to parameter 'x'" "true"
data class Foo(<caret>x: Int, val y: Int) {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddValVarToConstructorParameterAction$QuickFix