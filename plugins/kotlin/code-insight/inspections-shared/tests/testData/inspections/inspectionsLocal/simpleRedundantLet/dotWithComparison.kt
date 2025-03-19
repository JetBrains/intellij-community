// PROBLEM: none
// WITH_STDLIB

fun main(args: Array<String>) {
    args[0].let<caret> { it.isBlank() && it.toByteOrNull() != null }
}