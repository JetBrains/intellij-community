// FIX: Replace with 'firstOrNull()'
// WITH_STDLIB

fun test(values: IntArray): Int? {
    return <caret>if (values.isNotEmpty()) {
        values.first()
    } else {
        null
    }
}
