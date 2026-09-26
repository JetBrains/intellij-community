// FIX: Replace with 'firstOrNull()'
// WITH_STDLIB

fun test(list: List<String>): String? {
    return <caret>if (0 < list.size) {
        list[0]
    } else {
        null
    }
}
