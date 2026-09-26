// WITH_STDLIB
// FIX: none


open class A
open class B

inline fun <reified T : B, R: A>foo(list: List<R>) {
    val filteredList = list.filter<caret>IsInstance<T>()
}