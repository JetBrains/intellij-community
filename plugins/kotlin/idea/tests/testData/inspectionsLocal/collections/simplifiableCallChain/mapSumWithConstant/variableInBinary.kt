// WITH_STDLIB
fun test(list: List<Int>, i: Int) {
    list.map<caret> { 1 + i }.sum()
}