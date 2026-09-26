// PROBLEM: 'if' expression has identical branches
// FIX: Collapse 'if' expression

fun test(flag: Boolean): Int = if (flag) <caret>42 else {
    // Keep the explanation from the discarded branch, too.
    42
}
