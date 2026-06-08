// WITH_STDLIB
// PROBLEM: none


inline fun <T, reified R>foo(list: List<T>) {
    val filteredList = list.filter<caret>IsInstance<R>()
}
