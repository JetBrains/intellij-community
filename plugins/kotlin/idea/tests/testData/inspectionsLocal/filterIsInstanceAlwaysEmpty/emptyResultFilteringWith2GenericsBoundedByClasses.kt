// WITH_STDLIB
// FIX: none
// IGNORE_K1

open class A
open class B

inline fun <reified T : B, R: A>foo(list: List<R>) {
    val filteredList = list.filter<caret>IsInstance<T>()
}