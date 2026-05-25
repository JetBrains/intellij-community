// PROBLEM: none
// WITH_STDLIB

fun test(list: List<Int>): Int? {
    return <caret>if (list.count { it > 0 } > 0) {
        list[0]
    } else {
        null
    }
}
