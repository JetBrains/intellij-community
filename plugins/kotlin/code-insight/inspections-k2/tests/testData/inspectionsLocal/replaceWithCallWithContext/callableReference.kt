// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.4

fun String.shout() = uppercase()

fun main() {
    <caret>with("hi") {
        val ref = ::shout
        ref()
    }
}