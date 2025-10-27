// WITH_STDLIB
fun test(map: Map<Int, Int>): Map<Int, Int> {
    return <caret>if (map.isNotEmpty()) {
        map
    } else {
        mapOf(1 to 2)
    }
}