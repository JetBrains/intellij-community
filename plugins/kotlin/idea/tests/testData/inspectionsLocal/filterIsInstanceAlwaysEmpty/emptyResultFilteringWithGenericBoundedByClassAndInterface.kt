// WITH_STDLIB
// FIX: none


open class A

interface B

fun <T>foo(list: List<T>) where T: A, T: B {
    val filteredList = list.filte<caret>rIsInstance<Int>()
}