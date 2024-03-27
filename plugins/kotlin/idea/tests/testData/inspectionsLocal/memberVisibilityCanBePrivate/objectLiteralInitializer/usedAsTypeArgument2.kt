// IGNORE_FE10_BINDING_BY_FIR
// PROBLEM: none
// WITH_STDLIB
interface I

class Test {
    fun <caret>foo() = object : I {}
    fun bar() = listOf(foo())
    val baz = foo()
}