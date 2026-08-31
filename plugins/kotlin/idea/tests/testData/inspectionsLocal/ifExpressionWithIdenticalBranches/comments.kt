// PROBLEM: 'if' expression has identical branches
// FIX: Collapse 'if' expression

fun test(flag: Boolean): Int = if (flag) {
    // Keep this explanation1,
    // Keep this explanation2
    <caret>42
} else 42
