// WITH_STDLIB
fun test(list: List<Int>?) {
    if ((<caret>list == null) || list.isEmpty()) println(1) else println(0)
}