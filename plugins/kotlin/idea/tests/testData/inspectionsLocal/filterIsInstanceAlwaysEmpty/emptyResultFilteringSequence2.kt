// WITH_STDLIB
// FIX: none
// IGNORE_K1

class A

fun foo() {
    val sequence = sequenceOf(A())
    val filteredSequence = sequence.filte<caret>rIsInstance(Int::class.java)
}