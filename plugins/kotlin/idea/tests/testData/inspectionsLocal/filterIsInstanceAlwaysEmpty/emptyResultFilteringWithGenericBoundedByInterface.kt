// WITH_STDLIB
// FIX: none


interface A

fun <T: A>foo(list: List<T>) {
    val filteredList = list.filterIsI<caret>nstance<Int>()
}
