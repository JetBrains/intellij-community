// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

fun process(data: String) {}

fun test() {
    <caret>process(data = "hello")
}