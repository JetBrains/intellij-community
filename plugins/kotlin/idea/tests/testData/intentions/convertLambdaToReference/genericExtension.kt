// WITH_STDLIB
fun List<Int>.foo() = firstOrNull()

fun main() {
    listOf(1, 2, 3).apply {<caret> foo() }
}
