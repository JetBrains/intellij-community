// FIX: Use property access syntax

fun main() {
    val j = J()
    j.getThis().<caret>setX(1)
}