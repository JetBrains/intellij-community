fun main() {
    val list = listOf(1)
    val a = setOf(1, 2) +
            list.map { it + 1} +
            list.mapNotNull { it + 1} +<caret>
            list.mapIndexed { i, e -> e - 2 } +
            list.filter { it % 2 == 1 } +
            list.filterNot { it % 2 == 1 } +
            list.filterNotNull() +
            list.filterIsInstance<Int>() +
            5
}