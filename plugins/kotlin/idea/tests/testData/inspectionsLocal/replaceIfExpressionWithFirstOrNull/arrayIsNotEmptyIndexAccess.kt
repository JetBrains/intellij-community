// FIX: Replace with 'firstOrNull()'
// WITH_STDLIB

fun test(): String? {
    val xs = arrayOf("a", "b")
    return <caret>if (xs.isNotEmpty()) xs[0] else null
}