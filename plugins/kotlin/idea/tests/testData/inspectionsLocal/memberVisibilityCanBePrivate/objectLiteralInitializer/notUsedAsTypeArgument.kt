// IGNORE_FE10_BINDING_BY_FIR
// WITH_STDLIB
interface I

class Test {
    val <caret>foo = object : I {}
    val bar = foo
}