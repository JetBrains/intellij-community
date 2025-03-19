// FIR_IDENTICAL
// FIR_COMPARISON
class X(p1: Int, p2: Int = p<caret>, p3: Int)

// EXIST: p1
// ABSENT: p2
// ABSENT: p3

