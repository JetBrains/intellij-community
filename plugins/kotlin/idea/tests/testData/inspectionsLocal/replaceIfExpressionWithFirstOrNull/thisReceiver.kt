// FIX: Replace with 'firstOrNull()'
// WITH_STDLIB

fun IntArray.test(): Int? {
    return <caret>if (this.size > 0) {
        this[0]
    } else {
        null
    }
}
