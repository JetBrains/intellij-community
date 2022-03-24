// "Change type to MutableMap" "true"
// WITH_STDLIB
fun main() {
    val map = mapOf(1 to "a")
    map[2<caret>] = "b"
}