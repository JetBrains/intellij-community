// WITH_STDLIB
fun test(list: List<Int>?) {
    if (<caret>list != null && list.isNotEmpty()) println(list.size) else println(0)
}
