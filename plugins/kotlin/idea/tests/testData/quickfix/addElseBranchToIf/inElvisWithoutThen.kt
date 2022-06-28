// "Add else branch" "false"
// ACTION: Introduce local variable
// ACTION: Remove braces from 'while' statement

fun foo(x: String?) {
    while (true) {
        x ?: i<caret>f (x == null)
    }
}