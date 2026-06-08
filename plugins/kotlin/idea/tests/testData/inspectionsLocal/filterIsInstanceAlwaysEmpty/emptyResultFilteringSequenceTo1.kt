// WITH_STDLIB
// FIX: none


class A

fun foo() {
    val sequence = sequenceOf(A())
    val filteredSequence = sequence.filter<caret>IsInstanceTo<Int, MutableList<Int>>(mutableListOf())
}