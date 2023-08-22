// WITH_STDLIB
fun test(list: List<Int>?, b: Boolean) {
    if ((<caret>list == null || list.isEmpty()) && b) println(1) else println(0)
}