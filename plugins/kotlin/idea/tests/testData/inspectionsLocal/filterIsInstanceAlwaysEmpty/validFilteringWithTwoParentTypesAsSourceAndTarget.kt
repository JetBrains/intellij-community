// WITH_STDLIB
// PROBLEM: none


interface I
open class Base
class Impl : Base(), I

fun foo() {
    val list: List<Base> = listOf(Impl())
    val filteredList = list.filter<caret>IsInstance<I>()
}