// WITH_STDLIB
// PROBLEM: none


open class A

class B: A()

fun foo() {
    val array = arrayOf(A())
    val filteredArray = array.filterI<caret>sInstanceTo(mutableListOf(), B::class.java)
}