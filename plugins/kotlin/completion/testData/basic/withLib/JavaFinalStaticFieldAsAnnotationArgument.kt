// FIR_COMPARISON
// FIR_IDENTICAL

annotation class A(val n: Int)

@A(JavaClass.<caret>)
class B

// EXIST: field
// EXIST: stringField
// ABSENT: nonPrimitivefield
// ABSENT: nonFinalField
// ABSENT: arrayField