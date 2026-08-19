// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

context(s: String)
fun foo(a: Int, b: Int = 1) {}

fun test() {
    foo(s = "hello"<caret>, a = 1)
}