// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-receivers
// K2_ERROR: CONTEXT_RECEIVERS_DEPRECATED

context(Int)
fun other() {

}

fun Int<caret>.test() {
    other()
}