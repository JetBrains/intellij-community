// K2_ERROR: Syntax error: Incorrect template argument.
fun test(): String {
    return <caret>"${}${1 + 1}abc"
}
