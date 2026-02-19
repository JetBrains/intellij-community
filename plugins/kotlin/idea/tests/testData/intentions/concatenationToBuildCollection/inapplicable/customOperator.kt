// IS_APPLICABLE: false

fun main() {
    val a = listOf(1) +<caret> listOf(2)
}

operator fun List<Int>.plus(other: List<Int>): List<Int> = emptyList()

