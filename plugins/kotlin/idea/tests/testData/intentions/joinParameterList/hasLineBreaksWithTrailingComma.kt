// INTENTION_TEXT: "Put parameters on one line"
// SET_TRUE: ALLOW_TRAILING_COMMA
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'c' is never used

fun test(
    a: Int,
    b: Int,<caret>
    c: Int,
) {}