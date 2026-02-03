// WITH_STDLIB
fun test(list: ArrayList<Int>?) {
    if (<caret>list != null && list.isNotEmpty()) println(list.size) else println(0)
}
