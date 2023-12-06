// FIR_COMPARISON

annotation class A(val n: Int)

@A(JavaClass.<caret>)
class B

// EXIST: field
// EXIST: stringField
// ABSENT: nonPrimitivefield
// ABSENT: nonFinalField
// ABSENT: arrayField