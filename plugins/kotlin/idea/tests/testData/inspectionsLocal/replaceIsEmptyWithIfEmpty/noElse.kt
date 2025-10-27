// PROBLEM: none
// WITH_STDLIB
fun test(list: List<Int>) {
    <caret>if (list.isEmpty()) {
        listOf(1)
    }
}