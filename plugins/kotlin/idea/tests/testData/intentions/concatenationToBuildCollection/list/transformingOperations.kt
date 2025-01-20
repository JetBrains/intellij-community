fun main() {
    val list = listOf(1)
    val a = listOf(1, 2) +<caret>
            list.map { it + 1} +
            list.mapNotNull { it + 1} +
            list.mapIndexed { i, e -> e - 2 } +
            list.filter { it % 2 == 1 } +
            list.filterNot { it % 2 == 1 } +
            list.filterNotNull() +
            list.filterIsInstance<Int>() +
            5
}