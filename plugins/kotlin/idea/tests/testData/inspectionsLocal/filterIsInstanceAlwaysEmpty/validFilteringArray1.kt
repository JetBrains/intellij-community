// WITH_STDLIB
// PROBLEM: none


open class A

class B: A()

fun foo() {
    val array = arrayOf(A())
    val filteredArray = array.filt<caret>erIsInstance<B>()
}