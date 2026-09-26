// PROBLEM: 'if' expression has identical branches
// FIX: Collapse 'if' expression

fun test(): Int = if (true) { <caret>42 } else 42
