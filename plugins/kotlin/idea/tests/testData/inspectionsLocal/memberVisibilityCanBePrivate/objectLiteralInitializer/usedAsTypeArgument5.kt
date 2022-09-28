// IGNORE_FE10_BINDING_BY_FIR
// PROBLEM: none
// WITH_STDLIB
interface I

class Test(b: Boolean) {
    val <caret>foo = object : I {}
    val bar = if (b) listOf(foo) else listOf(foo, foo)
    val baz = foo
}