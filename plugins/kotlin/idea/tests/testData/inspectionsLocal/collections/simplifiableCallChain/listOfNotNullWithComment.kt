// WITH_STDLIB

fun test() {
    <caret>listOf(
            true, // comment1
            null // comment2
    ).filterNotNull().first()
}