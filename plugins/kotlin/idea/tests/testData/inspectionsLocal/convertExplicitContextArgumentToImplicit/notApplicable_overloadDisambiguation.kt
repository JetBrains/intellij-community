// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

context(value: Any)
fun foo() {}

context(string: String)
fun foo() {}

fun test() {
    <caret>foo(value = "")
}
