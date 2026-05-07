// FIX: Replace with 'firstOrNull()'
// WITH_STDLIB

fun test(values: IntArray): Int? {
    return <caret>if (values.size < 1) {
        null
    } else {
        values[0]
    }
}
