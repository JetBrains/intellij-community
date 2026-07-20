fun main() {
    val list = listOf(1)
    val a = setOf(1, 2) +
            list.map<Int, Int> { it + 1 } +
            list.mapNotNull<Int, Int> { it + 1 } +<caret>
            list.mapIndexed<Int, Int> { i, e -> e - 2 } +
            list.filter<Int> { it % 2 == 1 } +
            list.filterNot<Int> { it % 2 == 1 } +
            list.filterNotNull<Int>() +
            list.filterIsInstance<Int>() +
            5
}