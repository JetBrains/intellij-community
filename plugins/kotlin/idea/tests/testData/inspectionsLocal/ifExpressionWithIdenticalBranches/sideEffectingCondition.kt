// HIGHLIGHT: WARNING
// PROBLEM: 'if' expression has identical branches
// FIX: Collapse 'if' expression (may change semantics)

fun nextFlag(): Boolean = true

fun test(): Int = if (nextFlag()) <caret>42 else 42
