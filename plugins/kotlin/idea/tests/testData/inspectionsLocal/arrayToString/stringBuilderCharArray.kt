// PROBLEM: none

// WITH_STDLIB

fun main() {
    val chars = charArrayOf('a', 'b', 'c')
    val sb = StringBuilder()
    sb.<caret>append(chars)
}