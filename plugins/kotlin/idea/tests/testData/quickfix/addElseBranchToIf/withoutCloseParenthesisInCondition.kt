// "Add else branch" "false"
// ACTION: Enable option 'Local variable types' for 'Types' inlay hints
// ACTION: Replace property initializer with 'if' expression
// ERROR: 'if' must have both main and 'else' branches if used as an expression
// K2_AFTER_ERROR: 'if' must have both main and 'else' branches when used as an expression.

fun foo(x: String?) {
    val a = i<caret>f (
}