// K2_ERROR: SYNTAX
fun test(): String {
    return <caret>"${}${1 + 1}abc"
}
