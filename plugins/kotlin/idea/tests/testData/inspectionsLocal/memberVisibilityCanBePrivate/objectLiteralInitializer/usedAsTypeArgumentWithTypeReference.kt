// WITH_STDLIB
interface I

class Test {
    val <caret>foo: I = object : I {}
    val bar = listOf(foo)
}