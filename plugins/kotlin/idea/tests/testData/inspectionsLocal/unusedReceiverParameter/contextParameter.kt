// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// IGNORE_K1

context(_: Int)
fun other() {

}

fun Int<caret>.test() {
    other()
}