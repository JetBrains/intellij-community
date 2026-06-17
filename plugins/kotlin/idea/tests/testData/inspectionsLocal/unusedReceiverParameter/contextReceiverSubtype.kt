// PROBLEM: none
// K2_ERROR: CONTEXT_PARAMETER_WITHOUT_NAME

interface A
class B : A

context(A)
fun other() {

}

fun B<caret>.test() {
    other()
}