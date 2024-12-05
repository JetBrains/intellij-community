// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// IGNORE_K1
// IGNORE_K2
// Obtaining context parameters is not yet implemented in the analysis so we ignore

context(_: Int)
fun other() {

}

fun Int<caret>.test() {
    other()
}