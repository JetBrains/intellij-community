// PROBLEM: 'if' expression has identical branches
// FIX: Collapse 'if' expression

fun test(flag: Boolean): Nothing =
    if (flag) throw Exception() else <caret>throw Exception()
