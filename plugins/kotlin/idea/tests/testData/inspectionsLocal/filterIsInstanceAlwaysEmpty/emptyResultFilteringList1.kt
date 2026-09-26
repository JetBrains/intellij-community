// WITH_STDLIB
// FIX: none


class A

fun foo() {
    val list = listOf(A())
    val filteredList = list.filt<caret>erIsInstance<Int>()
}