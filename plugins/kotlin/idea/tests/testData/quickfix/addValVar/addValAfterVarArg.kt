// "Add 'val' or 'var' to parameter 'x'" "true"
class Foo(vararg <caret>x: Int, val y: Int) {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddValVarToConstructorParameterAction$Intention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.AddValVarToConstructorParameterActionIntention