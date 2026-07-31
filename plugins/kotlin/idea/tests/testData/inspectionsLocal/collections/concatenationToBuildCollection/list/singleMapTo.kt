// FIX: Convert to collection builder

fun main() {
    val nums = listOf(1, 2)
    val a = nums.ma<caret>p { it * 2 }
}
