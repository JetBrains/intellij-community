// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
context(<caret>s: String)
fun test() {
    val x = s
    println(x)
}