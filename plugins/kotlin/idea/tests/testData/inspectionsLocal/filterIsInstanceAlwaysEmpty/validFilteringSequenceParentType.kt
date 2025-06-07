// WITH_STDLIB
// PROBLEM: none
// IGNORE_K1

open class A

class B: A()

fun foo() {
    val sequence = sequenceOf(B())
    val filteredSequence = sequence.filt<caret>erIsInstance<A>()
}