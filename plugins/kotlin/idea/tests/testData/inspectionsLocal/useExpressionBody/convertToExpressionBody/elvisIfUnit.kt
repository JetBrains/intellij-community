// PROBLEM: none
// K2-ERROR: 'if' must have both main and 'else' branches when used as an expression.
// ERROR: 'if' must have both main and 'else' branches if used as an expression

fun nullable() {}

fun bar() {}

fun foo(f: Boolean) {
    <caret>nullable() ?: if (f) bar()
}