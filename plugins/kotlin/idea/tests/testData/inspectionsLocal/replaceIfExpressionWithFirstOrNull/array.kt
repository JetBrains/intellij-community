// FIX: Replace with 'firstOrNull()'
// WITH_STDLIB

fun test(values: IntArray): Int? {
    return <caret>if (values.size != 0) {
        values[0]
    } else {
        null
    }
}
