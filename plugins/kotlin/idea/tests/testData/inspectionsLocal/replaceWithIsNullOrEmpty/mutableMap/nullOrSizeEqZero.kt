// WITH_STDLIB
fun test(map: MutableMap<Int, String>?) {
    val x = <caret>map == null || map.size == 0
}
