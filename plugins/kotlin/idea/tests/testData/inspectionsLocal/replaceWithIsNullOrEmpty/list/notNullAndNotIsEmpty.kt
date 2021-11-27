// WITH_STDLIB
fun test(list: List<Int>?) {
    if (<caret>list != null && !list.isEmpty()) println(list.size) else println(0)
}
