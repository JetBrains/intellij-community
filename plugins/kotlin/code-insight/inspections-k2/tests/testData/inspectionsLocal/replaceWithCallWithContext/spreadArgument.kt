// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.4

context(x: String)
fun foo1(count: Int): String = x.repeat(count)

fun with(vararg values: String, block: String.() -> Unit) {
    values.first().block()
}

fun main() {
    val args = arrayOf("hi")
    <caret>with(*args) { foo1(2) }
}