// WITH_STDLIB
// FIX: none


class A

fun foo() {
    val sequence = sequenceOf(A())
    val filteredSequence = sequence.filte<caret>rIsInstance(Int::class.java)
}