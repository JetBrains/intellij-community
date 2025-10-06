// WITH_STDLIB
fun test(list: List<Int>, i: Int) {
    list.map<caret> { if (i == 1) i else if (i == 2) 2 else 3 }.sum()
}