// WITH_STDLIB
// PROBLEM: none
// IGNORE_K1

interface I
open class Base
class Impl : Base(), I

fun foo() {
    val list: List<Base> = listOf(Impl())
    val filteredList = list.filter<caret>IsInstance<I>()
}