// PROBLEM: none
// WITH_STDLIB
fun test(list: List<Int>): List<Int> {
    return <caret>if (list.isEmpty()) {
        listOf(1)
    } else {
        list + list
    }
}