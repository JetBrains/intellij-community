// WITH_STDLIB
interface I

class Test {
    val <caret>foo = object : I {}
    val bar: List<I> = listOf(foo)
}
