// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
context(s: String) fun usesString() {}

context(<caret>s: String)
fun test() {
    usesString()
}