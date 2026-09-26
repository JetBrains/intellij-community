// HIGHLIGHT: WARNING
// PROBLEM: 'if' expression has identical branches
// FIX: Collapse 'if' expression (may change semantics)

fun test(): Int {
    val flag by lazy { true }
    return if (flag) <caret>42 else 42
}
