// WITH_STDLIB
// PROBLEM: none
// IGNORE_K1

open class A

class B: A()

fun foo() {
    val list = listOf(A())
    val filteredList = list.filt<caret>erIsInstance(B::class.java)
}