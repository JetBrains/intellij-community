// FIR_COMPARISON
// FIR_IDENTICAL

const val constString: String = ""

fun String.extFun(): Int = 1

annotation class A(val n: Int)

@A(constString.<caret>)
class B

// EXIST: length
// ABSENT: hashCode
// ABSENT: extFun