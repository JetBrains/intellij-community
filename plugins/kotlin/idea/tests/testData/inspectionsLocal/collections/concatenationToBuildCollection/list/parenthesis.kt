// FIX: Convert to collection builder

fun main() {
    val list = listOf(1)
    val a = listOf(1, 2) +<caret> (listOf(1,2)) + (listOf(1) - 1)
}
