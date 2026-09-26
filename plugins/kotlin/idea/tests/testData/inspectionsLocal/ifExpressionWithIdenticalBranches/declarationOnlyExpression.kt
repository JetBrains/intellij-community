// PROBLEM: 'if' expression has identical branches
// FIX: Collapse 'if' expression

fun test(flag: Boolean): Unit = if (flag) {
    <caret>val value = 42
} else {
    val value = 42
}
