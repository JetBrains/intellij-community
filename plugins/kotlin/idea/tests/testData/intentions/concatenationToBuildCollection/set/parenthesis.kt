fun main() {
    val list = listOf(1)
    val a = setOf(1, 2) +<caret> (listOf(1,2)) + (listOf(1) - 1)
}
