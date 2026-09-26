// WITH_STDLIB
// PROBLEM: none


interface A

interface B

fun <T>foo(list: List<T>) where T: A, T: B {
    val filteredList = list.filte<caret>rIsInstance<B>()
}