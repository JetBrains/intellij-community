// FIR_IDENTICAL
// FIR_COMPARISON
val v = 1

fun foo(@[S<caret>) { }

// INVOCATION_COUNT: 0
// EXIST: Suppress
// ABSENT: String
// ABSENT: v
