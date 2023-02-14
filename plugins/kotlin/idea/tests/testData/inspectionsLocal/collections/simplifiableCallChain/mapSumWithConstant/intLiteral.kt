// PROBLEM: none
// WITH_STDLIB
fun test(list: List<Int>) {
    list.map<caret> { 1 }.sum()
}