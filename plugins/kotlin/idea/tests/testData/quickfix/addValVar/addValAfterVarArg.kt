// "Add 'val' or 'var' to parameter 'x'" "true"
class Foo(vararg <caret>x: Int, val y: Int) {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddValVarToConstructorParameterAction$Intention