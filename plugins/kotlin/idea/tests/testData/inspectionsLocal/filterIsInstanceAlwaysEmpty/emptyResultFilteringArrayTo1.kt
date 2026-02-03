// WITH_STDLIB
// FIX: none
// IGNORE_K1

class A

fun foo() {
    val array = arrayOf(A())
    val filteredArray = array.filter<caret>IsInstanceTo<Int, MutableList<Int>>(mutableListOf())
}