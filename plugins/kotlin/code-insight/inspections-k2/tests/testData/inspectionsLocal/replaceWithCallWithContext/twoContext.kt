// "Replace 'with' with 'context'" "true"
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.4

context(x: String)
fun length(): Int = x.length

context(x: String)
fun repeated(n: Int): String = x.repeat(n)

fun main() {
    <caret>with("hi") {
        length()
        repeated(2)
    }
}