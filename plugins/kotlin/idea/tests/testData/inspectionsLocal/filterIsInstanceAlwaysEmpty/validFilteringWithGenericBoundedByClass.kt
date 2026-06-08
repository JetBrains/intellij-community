// WITH_STDLIB
// PROBLEM: none


open class A

class B: A()

fun <T: A>foo(list: List<T>) {
    val filteredList = list.filter<caret>IsInstance<B>()
}
