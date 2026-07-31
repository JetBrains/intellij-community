class WeirdMap(
    delegate: Map<String, Int>,
) : Map<String, Int> by delegate {
    operator fun iterator(): Iterator<Int> = listOf(1, 2).iterator()
}

fun test(map: WeirdMap) {
    map<caret>
}