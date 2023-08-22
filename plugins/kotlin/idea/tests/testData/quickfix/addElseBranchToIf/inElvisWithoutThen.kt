// "Add else branch" "false"
// ACTION: Introduce local variable
// ACTION: Remove braces from 'while' statement
// ERROR: 'if' must have both main and 'else' branches if used as an expression

fun foo(x: String?) {
    while (true) {
        x ?: i<caret>f (x == null)
    }
}