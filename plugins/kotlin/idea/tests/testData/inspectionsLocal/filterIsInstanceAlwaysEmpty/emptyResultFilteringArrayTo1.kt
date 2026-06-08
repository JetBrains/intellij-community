// WITH_STDLIB
// FIX: none


class A

fun foo() {
    val array = arrayOf(A())
    val filteredArray = array.filter<caret>IsInstanceTo<Int, MutableList<Int>>(mutableListOf())
}