// IS_APPLICABLE: false
// WITH_STDLIB
fun main() {
    with (listOf(1, 2, 3)) {
        1.apply {<caret> this@with.size }
    }
}
