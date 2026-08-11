// ERROR: 'if' must have both main and 'else' branches if used as an expression
// IS_APPLICABLE: false
// K2_ERROR: INVALID_IF_AS_EXPRESSION

fun test(b: Boolean): Int {
    return<caret> if (b)
}