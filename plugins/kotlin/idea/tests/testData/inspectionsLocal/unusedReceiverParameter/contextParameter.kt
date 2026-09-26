// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters


context(_: Int)
fun other() {

}

fun Int<caret>.test() {
    other()
}