// WITH_STDLIB
fun test(list: List<Int>?) {
    list?.forEach<caret> { println(it) }
}