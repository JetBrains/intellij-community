// FIX: Replace with 'firstOrNull()'
// WITH_STDLIB

fun test(values: IntArray): Int? {
    return <caret>if (values.isEmpty()) {
        null
    } else {
        values[0]
    }
}
