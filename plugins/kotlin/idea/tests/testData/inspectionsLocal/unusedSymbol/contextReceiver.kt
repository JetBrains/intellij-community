// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-receivers
// K2_ERROR: Experimental context receivers are superseded by context parameters.<br>Remove the '-Xcontext-receivers' compiler argument and migrate to the new syntax.<br><br>See the context parameters proposal for more details: https://kotl.in/context-parameters
// K2_ERROR: Unresolved label.

context(String<caret>)
fun stringFromContext(): String {
    return this@String
}