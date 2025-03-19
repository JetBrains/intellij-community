// WITH_STDLIB
// FIX: none
// IGNORE_K1

interface A

fun <T: A>foo(list: List<T>) {
    val filteredList = list.filterIsI<caret>nstance<Int>()
}
