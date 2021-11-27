// WITH_STDLIB
fun test(set: MutableSet<Int>?) {
    val x = <caret>set == null || set.size == 0
}
