// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-receivers

interface A
class B : A

context(A)
fun other() {

}

fun B<caret>.test() {
    other()
}