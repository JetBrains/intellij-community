// WITH_STDLIB
// PROBLEM: none


open class A

class B: A()

fun foo() {
    val sequence = sequenceOf(B())
    val filteredSequence = sequence.filt<caret>erIsInstanceTo<A, MutableList<A>>(mutableListOf())
}