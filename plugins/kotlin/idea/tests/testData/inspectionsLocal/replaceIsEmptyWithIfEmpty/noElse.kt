// PROBLEM: none
// WITH_STDLIB
fun test(list: List<Int>) {
    if (list.isEmpty<caret>()) {
        listOf(1)
    }
}