// PROBLEM: none
// ERROR: 'if' must have both main and 'else' branches if used as an expression
// K2_ERROR: INVALID_IF_AS_EXPRESSION

fun nullable() {}

fun bar() {}

fun foo(f: Boolean) {
    <caret>nullable() ?: if (f) bar()
}