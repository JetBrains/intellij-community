// WITH_STDLIB
fun test(list: List<Int>) {
    list.map<caret> { 1L }.sum()
}
