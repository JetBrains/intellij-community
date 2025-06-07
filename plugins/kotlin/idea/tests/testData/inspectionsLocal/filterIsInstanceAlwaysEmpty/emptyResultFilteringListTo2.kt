// WITH_STDLIB
// FIX: none
// IGNORE_K1

class A

fun foo() {
    val list = listOf(A())
    val filteredList = list.filt<caret>erIsInstanceTo(mutableListOf(), Int::class.java)
}