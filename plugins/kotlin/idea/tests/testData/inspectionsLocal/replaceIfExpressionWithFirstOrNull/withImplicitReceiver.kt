// FIX: Replace with 'firstOrNull()'
// WITH_STDLIB

fun test(values: IntArray): Int? {
    return with(values) {
        <caret>if (isNotEmpty()) {
            values[0]
        } else {
            null
        }
    }
}
