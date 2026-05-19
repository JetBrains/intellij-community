// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

fun greet(name: String) {}

fun main() {
    val name = "World"
    greet(<caret>name = name)
}
