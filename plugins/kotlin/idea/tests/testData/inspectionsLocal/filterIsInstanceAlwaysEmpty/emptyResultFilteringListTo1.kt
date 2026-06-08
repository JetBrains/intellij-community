// WITH_STDLIB
// FIX: none


class A

fun foo() {
    val list = listOf(A())
    val filteredList = list.filt<caret>erIsInstanceTo<Int, MutableList<Int>>(mutableListOf())
}