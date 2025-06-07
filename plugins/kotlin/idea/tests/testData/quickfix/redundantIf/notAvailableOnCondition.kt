// "Remove redundant 'if' statement" "false"
// ACTION: Expand boolean expression to 'if else'
// ACTION: Introduce local variable
// ACTION: Remove braces from all 'if' statements
fun bar(value: Int): Boolean {
    if (<caret>value % 2 == 0) {
        return true
    } else {
        return false
    }
}