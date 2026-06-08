// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
// LANGUAGE_VERSION: 2.3
// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments
// IS_APPLICABLE: false

context(s: String)
fun foo(a: Int) {}

fun test() {
    foo(a = 1, s = "x"<caret>)
}