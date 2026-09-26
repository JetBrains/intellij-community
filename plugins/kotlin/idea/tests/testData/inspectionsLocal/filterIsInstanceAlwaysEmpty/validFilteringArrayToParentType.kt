// WITH_STDLIB
// PROBLEM: none


open class A

class B: A()

fun foo() {
    val array = arrayOf(B())
    val filteredArray = array.filterI<caret>sInstanceTo<A, MutableList<A>>(mutableListOf())
}