// PROBLEM: none
// WITH_STDLIB

fun test(list: List<String>): String? {
    return <caret>if (list.size > 0) {
        list[1]
    } else {
        null
    }
}
