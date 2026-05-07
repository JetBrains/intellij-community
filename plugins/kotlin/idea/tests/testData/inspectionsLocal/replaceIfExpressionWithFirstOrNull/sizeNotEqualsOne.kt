// PROBLEM: none
// WITH_STDLIB

fun test(list: List<String>): String? {
    return <caret>if (list.size != 1) {
        list[0]
    } else {
        null
    }
}
