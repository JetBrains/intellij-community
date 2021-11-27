// WITH_STDLIB
fun test(list: List<Int>?) {
    if (<caret>list != null && list.count() > 0) println(list.size) else println(0)
}
