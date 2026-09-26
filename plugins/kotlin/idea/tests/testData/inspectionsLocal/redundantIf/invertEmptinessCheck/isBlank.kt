// WITH_STDLIB
fun String.foo(): Boolean {
    <caret>if (isBlank()) return false
    return true
}
