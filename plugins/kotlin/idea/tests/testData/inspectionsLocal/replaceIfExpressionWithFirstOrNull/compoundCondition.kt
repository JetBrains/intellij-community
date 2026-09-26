// PROBLEM: none
// WITH_STDLIB

fun test(list: List<String>, predicate: Boolean): String? {
    return <caret>if (list.size > 0 && predicate) {
        list[0]
    } else {
        null
    }
}
