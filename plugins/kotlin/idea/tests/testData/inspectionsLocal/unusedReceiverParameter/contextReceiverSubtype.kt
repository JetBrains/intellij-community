// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-receivers
// K2_ERROR: CONTEXT_RECEIVERS_DEPRECATED

interface A
class B : A

context(A)
fun other() {

}

fun B<caret>.test() {
    other()
}