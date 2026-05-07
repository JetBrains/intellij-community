// PROBLEM: none
// WITH_STDLIB

fun test(list1: List<String>, list2: List<String>): String? {
    return <caret>if (list1.size > 0) {
        list2[0]
    } else {
        null
    }
}
