// WITH_STDLIB
fun test(map: Map<Int, Int>): Map<Int, Int> {
    return if (map.isEmpty<caret>()) {
        mapOf(1 to 2)
    } else {
        map
    }
}