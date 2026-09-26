// WITH_STDLIB
// PROBLEM: none


interface A

class B: A

fun <T: A>foo(list: List<T>) {
    val filteredList = list.filter<caret>IsInstance<B>()
}
