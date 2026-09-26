// WITH_STDLIB
// PROBLEM: none


open class A

class B: A()

fun foo() {
    val sequence = sequenceOf(A())
    val filteredSequence = sequence.filterIsI<caret>nstanceTo(mutableListOf(), B::class.java)
}