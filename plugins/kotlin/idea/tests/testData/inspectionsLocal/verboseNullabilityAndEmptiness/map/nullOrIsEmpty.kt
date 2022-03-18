// WITH_STDLIB
fun test(map: Map<Int, String>?) {
    if (<caret>map == null || map.isEmpty()) println(0) else println(map.size)
}
