// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
// LANGUAGE_VERSION: 2.3
// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

context(_: String)
fun foo(a: Int) {}

fun test() {
    foo(<caret>)
}