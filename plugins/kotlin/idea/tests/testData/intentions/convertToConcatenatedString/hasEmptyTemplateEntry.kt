// K2-ERROR: Syntax error: Incorrect template argument.
fun test(): String {
    return <caret>"${}${1 + 1}abc"
}
