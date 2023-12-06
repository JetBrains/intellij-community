// FIR_COMPARISON
// FIR_IDENTICAL

enum class EE {
    AA, BB;
}

annotation class A(val n: Array<EE>)

@A(<caret>)
class B

// EXIST: arrayOf
// EXIST: emptyArray
// ABSENT: intArrayOf
