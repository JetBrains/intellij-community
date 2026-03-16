// PROBLEM: none
// WITH_STDLIB
fun test(list: List<Int>): List<Int> {
    return <caret>if (!list.isEmpty()) {
        list
    } else {
        listOf(1)
    }
}