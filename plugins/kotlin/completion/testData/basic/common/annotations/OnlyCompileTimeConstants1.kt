// FIR_COMPARISON
// FIR_IDENTICAL

const val constInt: Int = 1
const val constString: String = ""

val nonConstInt: Int = 1
fun foo(): Int = 1

annotation class A(val n: Int)

@A(<caret>)
class B

// EXIST: B
// EXIST: constInt
// EXIST: constString
// ABSENT: arrayOf
// ABSENT: nonConstInt
// ABSENT: foo
// ABSENT: hashCode

// EXIST: if
// EXIST: true
// EXIST: false
// EXIST: when
// ABSENT: do
// ABSENT: for
// ABSENT: fun
// ABSENT: null
// ABSENT: object
// ABSENT: "object OnlyCompileTimeConstants1"
// ABSENT: throw
// ABSENT: try
// ABSENT: while
