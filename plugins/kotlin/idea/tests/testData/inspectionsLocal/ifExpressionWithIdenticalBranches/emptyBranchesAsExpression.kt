// PROBLEM: 'if' expression has identical branches
// FIX: none

fun test(flag: Boolean): Unit = if (flag) <caret>{} else {}
