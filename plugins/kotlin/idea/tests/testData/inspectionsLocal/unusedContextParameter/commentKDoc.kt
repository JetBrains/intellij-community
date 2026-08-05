// COMPILER_ARGUMENTS: -Xcontext-parameters
fun foo() {}

/**
 * Some documentation.
 */
context(<caret>s: String)
fun test() {
    foo()
}