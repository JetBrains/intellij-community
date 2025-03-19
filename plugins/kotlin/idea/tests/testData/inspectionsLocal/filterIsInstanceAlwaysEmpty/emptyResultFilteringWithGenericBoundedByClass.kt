// WITH_STDLIB
// FIX: none
// IGNORE_K1

open class A

fun <T: A>foo(list: List<T>) {
    val filteredList = list.filterIs<caret>Instance<Int>()
}
