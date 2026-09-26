// WITH_STDLIB
// FIX: none


class A

fun foo() {
    val list = listOf(A())
    val filteredList = list.filt<caret>erIsInstanceTo(mutableListOf(), Int::class.java)
}