// WITH_STDLIB
// FIX: none


class A

fun foo() {
    val array = arrayOf(A())
    val filteredArray = array.filt<caret>erIsInstanceTo(mutableListOf(), Int::class.java)
}