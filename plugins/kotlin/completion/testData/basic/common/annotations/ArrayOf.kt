// FIR_COMPARISON
// FIR_IDENTICAL

annotation class A(val n: IntArray)

@A(<caret>)
class B

// EXIST: intArrayOf
// ABSENT: booleanArrayOf
// ABSENT: arrayOf
