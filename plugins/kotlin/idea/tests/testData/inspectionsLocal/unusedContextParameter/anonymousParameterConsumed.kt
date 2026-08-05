// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
context(s: String) fun usesString() {}

context(<caret>_: String)
fun test() {
    usesString()
}