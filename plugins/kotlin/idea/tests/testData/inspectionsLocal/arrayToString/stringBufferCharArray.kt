// PROBLEM: none

// WITH_STDLIB

fun main() {
    val chars = charArrayOf('a', 'b', 'c')
    val sb = StringBuffer()
    sb.<caret>append(chars)
}