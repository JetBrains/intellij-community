// WITH_STDLIB
// PROBLEM: none
// IGNORE_K1

interface A

open class B

class C: A, B()

fun <T>foo(list: List<T>) where T: A, T: B {
    val filteredList = list.filte<caret>rIsInstance<C>()
}