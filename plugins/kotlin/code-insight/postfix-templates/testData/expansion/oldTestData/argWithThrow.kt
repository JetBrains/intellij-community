// IGNORE_K2
// TEMPLATE: \tRuntimeException\t
fun foo(s: String) {
    try {
    } catch (e: Exception) {
        throw s.arg<caret>
    }
}