// PROBLEM: 'if' expression has identical branches
// FIX: none

fun test(flag: Boolean): Int = run {
    if (flag) {
        <caret>val value = 42
        return@run value
    } else {
        val value = 42
        return@run value
    }
}
