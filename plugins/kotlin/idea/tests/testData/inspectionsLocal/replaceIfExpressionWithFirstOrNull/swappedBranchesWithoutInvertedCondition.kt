// PROBLEM: none
// WITH_STDLIB

fun test(list: List<String>): String? {
    return <caret>if (list.size > 0) {
        null
    } else {
        list[0]
    }
}
