// WITH_STDLIB
fun test(list: List<Int>?) {
    if (<caret>list == null || list.count() == 0) println(0) else println(list.size)
}
