// FIX: Use property access syntax

// PROBLEM: "Use of setter method instead of property access syntax"

fun main() {
    val j = J()
    j.<caret>setX(1)
}