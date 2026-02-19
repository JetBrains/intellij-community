// FIR_COMPARISON
// FIR_IDENTICAL

const val flag: Boolean = true

annotation class A(val b: Boolean)

@A(flag.<caret>)
class B

// EXIST: not