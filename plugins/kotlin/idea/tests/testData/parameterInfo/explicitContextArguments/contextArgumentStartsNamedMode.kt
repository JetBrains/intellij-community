// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

context(s: String)
fun foo(i: Int) {}

fun test() {
    foo(s = "hello", <caret>1)
}
