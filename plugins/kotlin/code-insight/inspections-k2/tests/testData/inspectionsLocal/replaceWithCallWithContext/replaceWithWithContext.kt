// "Replace 'with' with 'context'" "true"
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.4

context(x: String)
fun foo1(count: Int): String {
    return x.repeat(count)
}

fun main() {
    <caret>with("string") { foo1(2) }
}