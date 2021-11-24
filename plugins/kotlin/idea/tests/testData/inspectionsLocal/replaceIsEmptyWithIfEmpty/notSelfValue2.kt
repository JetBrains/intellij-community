// PROBLEM: none
// WITH_STDLIB
fun test(list: List<Int>): List<Int> {
    return if (list.isEmpty<caret>()) {
        listOf(1)
    } else {
        println()
        list
    }
}