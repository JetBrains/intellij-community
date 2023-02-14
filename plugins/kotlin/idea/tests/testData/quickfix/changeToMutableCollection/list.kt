// "Change type to MutableList" "true"
// WITH_STDLIB
fun main() {
    val list = listOf(1, 2, 3)
    list[1]<caret> = 10
}