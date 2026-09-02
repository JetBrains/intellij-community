// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// LANGUAGE_VERSION: 2.5

fun String.shout() = uppercase()

fun main() {
    <caret>with("hi") {
        val ref = ::shout
        ref()
    }
}