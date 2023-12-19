// "Add else branch" "false"
// ACTION: Enable 'Types' inlay hints
// ACTION: Replace property initializer with 'if' expression
// ERROR: 'if' must have both main and 'else' branches if used as an expression

fun foo(x: String?) {
    val a = i<caret>f (
}