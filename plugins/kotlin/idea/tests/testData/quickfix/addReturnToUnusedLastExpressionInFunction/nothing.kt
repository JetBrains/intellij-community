// "Add 'return' before the expression" "false"
// ACTION: Do not show return expression hints

fun test(): Nothing {
    <caret>throw Throwable("Error")
}
