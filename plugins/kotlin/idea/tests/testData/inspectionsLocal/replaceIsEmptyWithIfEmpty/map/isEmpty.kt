// WITH_STDLIB
fun test(map: Map<Int, Int>): Map<Int, Int> {
    return <caret>if (map.isEmpty()) {
        mapOf(1 to 2)
    } else {
        map
    }
}