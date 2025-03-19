// PRIORITY: LOW
// AFTER-WARNING: Parameter 'a' is never used
fun foo(a: String) {}
inline fun <reified T> generic() = null as T

fun main() {
    foo(<caret>generic())
}