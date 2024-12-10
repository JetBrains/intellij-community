// ERROR: 'if' must have both main and 'else' branches if used as an expression
// IS_APPLICABLE: false

fun test(b: Boolean): Int {
    return<caret> if (b)
}