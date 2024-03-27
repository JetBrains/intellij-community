// WITH_STDLIB
interface I

class Test {
    val <caret>foo = object : I {}
    private val bar = listOf(foo)
}