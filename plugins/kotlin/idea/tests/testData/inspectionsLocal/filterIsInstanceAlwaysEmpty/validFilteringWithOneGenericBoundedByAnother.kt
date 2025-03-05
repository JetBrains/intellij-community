// WITH_STDLIB
// PROBLEM: none
// IGNORE_K1

open class A

inline fun <reified R : T, T : A> foo(list: List<T>) {
    val filteredList = list.filterIs<caret>Instance<R>()
}