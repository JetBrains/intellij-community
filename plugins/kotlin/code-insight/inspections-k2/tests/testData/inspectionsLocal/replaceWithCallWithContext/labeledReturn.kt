// "Replace 'with' with 'context'" "true"
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.4

context(x: String)
fun foo1(): String = x

fun main() {
    <caret>with("hi") {
        if (foo1().isEmpty()) return@with
        println("not empty")
    }
}