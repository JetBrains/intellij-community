// "Convert string to character literal" "false"
// ACTION: Expand boolean expression to 'if else'
// ERROR: Operator '==' cannot be applied to 'Char' and 'String'
// K2_AFTER_ERROR: Operator '==' cannot be applied to 'Char' and 'String'.
fun test(c: Char): Boolean {
    val a = "a"
    return <caret>c == "$a"
}
