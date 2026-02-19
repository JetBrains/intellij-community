// WITH_STDLIB
// FIX: none
// IGNORE_K1

class A

fun foo() {
    val list = listOf(A())
    val filteredList = list.filt<caret>erIsInstance(Int::class.java)
}