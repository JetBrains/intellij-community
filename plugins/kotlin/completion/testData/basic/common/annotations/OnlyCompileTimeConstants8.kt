// FIR_COMPARISON
// FIR_IDENTICAL
// LANGUAGE_VERSION: 2.5

const val constString: String = ""

val nonConstString: String = ""
fun foo(): String = ""

annotation class A(val s: String)

@A(<caret>)
class B

// EXIST: constString
// ABSENT: nonConstString
// ABSENT: foo
// ABSENT: hashCode
// ABSENT: uppercase
