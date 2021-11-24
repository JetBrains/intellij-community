// "Change type to MutableSet" "false"
// DISABLE-ERRORS
// ACTION: Converts the assignment statement to an expression
// ACTION: Replace overloaded operator with function call
// WITH_STDLIB
fun main() {
    val set = setOf(1, 2, 3)
    set[1]<caret> = 10
}