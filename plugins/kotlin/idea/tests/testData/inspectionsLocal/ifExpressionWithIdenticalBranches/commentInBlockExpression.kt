// PROBLEM: 'if' expression has identical branches
// FIX: Collapse 'if' expression

fun test(flag: Boolean): Int = if (flag) {
    // Keep this explanation.
    <caret>val result = 42
    result
} else {
    val result = 42
    result
}
