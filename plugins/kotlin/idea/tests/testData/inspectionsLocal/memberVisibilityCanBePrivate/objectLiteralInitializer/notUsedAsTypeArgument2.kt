// IGNORE_FE10_BINDING_BY_FIR
// WITH_STDLIB
interface I
interface J
class Test {
    val <caret>foo = object : I {}
    val bar = object : J {}
    val baz = listOf(foo, bar)
}
