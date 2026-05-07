// FIX: Replace with 'firstOrNull()'
// WITH_STDLIB

fun test(text: String): Char? {
    return <caret>if (text.isNotEmpty()) {
        text[0]
    } else {
        null
    }
}
