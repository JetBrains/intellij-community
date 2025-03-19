// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-receivers

context(Int)
fun other() {

}

fun Int<caret>.test() {
    other()
}