// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
context(s: String) fun usesString() {}
fun runIt(block: () -> Unit) = block()

fun test() {
    <caret>context("") {
        runIt { usesString() }
    }
}