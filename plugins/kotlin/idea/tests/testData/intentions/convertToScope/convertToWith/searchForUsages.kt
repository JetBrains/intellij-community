// WITH_STDLIB
enum class Color {
    RED, GREEN, BLUE;

    companion object {
        fun printEntries() {
            println()
        }
    }
}

fun main() {
    Color.<caret>printEntries()
}