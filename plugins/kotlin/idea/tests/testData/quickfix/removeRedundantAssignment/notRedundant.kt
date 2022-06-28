// "Remove redundant assignment" "false"
// ACTION: Converts the assignment statement to an expression
// ACTION: Do not show return expression hints
fun foo(): Int {
    var i = 1
    <caret>i = 2
    return i
}