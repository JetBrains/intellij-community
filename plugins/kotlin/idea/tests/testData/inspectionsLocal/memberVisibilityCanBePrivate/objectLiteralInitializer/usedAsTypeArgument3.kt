// IGNORE_FE10_BINDING_BY_FIR
// PROBLEM: none
// WITH_STDLIB
interface I

class Test {
    val <caret>foo
        get() = object : I {}
    val bar
        get() = listOf(foo)
    val baz = foo
}