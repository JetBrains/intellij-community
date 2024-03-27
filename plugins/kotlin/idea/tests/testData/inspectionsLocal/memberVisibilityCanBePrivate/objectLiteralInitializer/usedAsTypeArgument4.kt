// IGNORE_FE10_BINDING_BY_FIR
// PROBLEM: none
// WITH_STDLIB
interface I

class Test {
    companion object {
        val <caret>foo = object : I {}
        val bar = listOf(foo)
        val baz = foo
    }
}