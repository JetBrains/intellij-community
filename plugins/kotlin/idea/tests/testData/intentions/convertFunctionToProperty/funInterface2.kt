// PRIORITY: LOW
fun interface I {
    fun foo(): Int

    fun <caret>bar(): Int = 42
}