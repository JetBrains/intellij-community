// PROBLEM: none
// WITH_RUNTIME
// LANGUAGE_VERSION: 2.3
// K2_ERROR: UNSUPPORTED_CONTEXTUAL_DECLARATION_CALL
// K2_ERROR: UNSUPPORTED_FEATURE

context(x: String)
fun foo1(count: Int): String {
    return x.repeat(count)
}

fun main() {
    <caret>with("string") { foo1(2) }
}