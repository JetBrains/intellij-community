// WITH_STDLIB
// PROBLEM: none
// IGNORE_K1

inline fun <T, reified R>foo(list: List<T>) {
    val filteredList = list.filter<caret>IsInstance<R>()
}
