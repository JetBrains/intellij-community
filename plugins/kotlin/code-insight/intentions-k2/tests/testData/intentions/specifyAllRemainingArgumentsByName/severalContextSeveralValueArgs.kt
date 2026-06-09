// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
// LANGUAGE_VERSION: 2.3
// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

context(s: String, n: Int, m: String)
fun foo(a: Int, b: Int, c: String) {}

fun test() {
    foo(b = 1, a = 2, n = 5, s = ""<caret>)
}