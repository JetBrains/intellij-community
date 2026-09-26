// FIX: Replace with 'firstOrNull()'
// WITH_STDLIB

fun test(list: List<Int>): Int? {
    return <caret>if (list.count() > 0) {
        list[0]
    } else {
        null
    }
}
