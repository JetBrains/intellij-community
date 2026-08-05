// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
fun runIt(block: () -> Unit) = block()

context(<caret>s: String)
fun test() {
    runIt { println(s) }
}