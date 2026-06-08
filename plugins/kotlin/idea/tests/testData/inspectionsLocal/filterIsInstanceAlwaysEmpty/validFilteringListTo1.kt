// WITH_STDLIB
// PROBLEM: none


open class A

class B: A()

fun foo() {
    val list = listOf(A())
    val filteredList = list.fil<caret>terIsInstanceTo<B, MutableList<B>>(mutableListOf())
}