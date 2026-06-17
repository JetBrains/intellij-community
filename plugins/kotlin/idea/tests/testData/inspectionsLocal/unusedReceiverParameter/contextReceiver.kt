// PROBLEM: none
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

context(Int)
fun other() {

}

fun Int<caret>.test() {
    other()
}