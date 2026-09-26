// PROBLEM: none

// WITH_STDLIB

import java.io.PrintWriter

fun write(pw: PrintWriter) {
    val chars = charArrayOf('h', 'i')
    pw.<caret>print(chars)
}