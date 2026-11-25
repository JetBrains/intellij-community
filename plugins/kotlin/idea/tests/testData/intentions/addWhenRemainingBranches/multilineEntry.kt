// WITH_STDLIB
// Issue: KTIJ-38548
// K2_ERROR: NO_ELSE_IN_WHEN
enum class Color {
    RED, GREEN, BLUE, YELLOW
}

fun test(color: Color) {
    <caret>when (color) {
        Color.BLUE,
        Color.GREEN -> println("Blue or Green")
    }
}