// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

context(x: String, y: String)
fun foo(i: Int) {}

fun test() {
    foo(1, <caret>x = "first", y = "second")
}
