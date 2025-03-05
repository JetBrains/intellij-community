// TEMPLATE: \tRuntimeException\t
fun foo(s: String) {
    try {
    } catch (e: Exception) {
        RuntimeException(throw s)
    }
}