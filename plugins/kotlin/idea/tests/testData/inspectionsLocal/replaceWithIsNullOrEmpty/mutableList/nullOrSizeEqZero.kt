// WITH_STDLIB
fun test(list: MutableList<Int>?) {
    val x = <caret>list == null || list.size == 0
}
