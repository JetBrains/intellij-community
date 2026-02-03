// WITH_STDLIB
fun test2(list: List<Int>) {
    list.any<caret> { it == 1 }.let { println(it) }
}