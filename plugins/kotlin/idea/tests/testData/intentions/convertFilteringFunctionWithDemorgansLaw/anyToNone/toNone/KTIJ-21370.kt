// WITH_STDLIB
fun test(list: List<Int>, list2: List<Int>) {
    list.forEach { x ->
        !list2.any<caret> { it == x }
    }
}