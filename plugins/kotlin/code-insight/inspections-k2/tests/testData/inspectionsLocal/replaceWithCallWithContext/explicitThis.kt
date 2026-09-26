// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.4

context(x: String)
fun foo1(count: Int): String = x.repeat(count)

fun main() {
    <caret>with("hi") {
        val copy = this
        foo1(2)
    }
}