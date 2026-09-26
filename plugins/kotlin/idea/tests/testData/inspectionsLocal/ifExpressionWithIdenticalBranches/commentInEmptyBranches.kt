// PROBLEM: 'if' expression has identical branches
// FIX: none

fun test(flag: Boolean) {
    if (flag) <caret>{
        // Keep this explanation.
    } else {}
}
