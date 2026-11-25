// WITH_STDLIB
// Issue: KTIJ-38548
// K2_ERROR: NO_ELSE_IN_WHEN
// SET_FALSE: LINE_BREAK_AFTER_MULTILINE_WHEN_ENTRY
enum class Color {
    RED, GREEN, BLUE, YELLOW
}

fun test(color: Color) {
    <caret>when (color) {
        Color.BLUE,
        Color.GREEN -> println("Blue or Green")
    }
}