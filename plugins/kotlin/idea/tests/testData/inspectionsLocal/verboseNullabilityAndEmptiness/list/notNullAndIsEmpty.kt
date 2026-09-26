// WITH_STDLIB
// PROBLEM: none
fun test(list: List<Int>?) {
    val x = <caret>list != null && list.isEmpty()
}
