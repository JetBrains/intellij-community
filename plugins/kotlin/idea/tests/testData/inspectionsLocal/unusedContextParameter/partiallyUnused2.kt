// COMPILER_ARGUMENTS: -Xcontext-parameters
context(s: String, <caret>i: Int)
fun test() {
    println(s)
}