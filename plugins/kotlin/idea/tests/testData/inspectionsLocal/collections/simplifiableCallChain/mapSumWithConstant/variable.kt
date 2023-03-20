// WITH_STDLIB
fun test(list: List<Int>, i: Int) {
    list.map<caret> { i }.sum()
}