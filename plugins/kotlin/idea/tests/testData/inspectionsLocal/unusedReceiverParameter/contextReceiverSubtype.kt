// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-receivers
// K2_ERROR: Experimental context receivers are superseded by context parameters.<br>Replace the '-Xcontext-receivers' compiler argument with '-Xcontext-parameters' and migrate to the new syntax.<br><br>See the context parameters proposal for more details: https://kotl.in/context-parameters

interface A
class B : A

context(A)
fun other() {

}

fun B<caret>.test() {
    other()
}