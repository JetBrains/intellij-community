// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.4

fun main() {
    <caret>with("hi") {
        println("not used")
    }
}