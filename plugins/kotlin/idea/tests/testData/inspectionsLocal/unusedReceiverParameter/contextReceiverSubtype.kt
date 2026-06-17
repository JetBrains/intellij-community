// PROBLEM: none
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

interface A
class B : A

context(A)
fun other() {

}

fun B<caret>.test() {
    other()
}