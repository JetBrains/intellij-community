// WITH_STDLIB
// PROBLEM: none
fun test(list: List<String>) {
    val x = 42 in 0 <caret>until list.lastIndex
}
