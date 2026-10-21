// FIR_COMPARISON
// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -XXLanguage:-IntrinsicConstEvaluation

const val constString: String = ""

annotation class A(val s: String)

@A(constString.<caret>)
class B

// EXIST: length
// ABSENT: lowercase
// ABSENT: uppercase
// ABSENT: trim
// ABSENT: trimStart
// ABSENT: trimEnd
