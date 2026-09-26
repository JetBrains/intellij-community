// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

context(first: String, second: Int)
fun foo(i: String) {}

fun test() {
    foo("value", first = "a", second = <caret>)
}
