// COMPILER_ARGUMENTS: -Xcontext-parameters
fun foo() {}

// keep this doc comment
context(<caret>s: String)
fun test() {
    foo()
}