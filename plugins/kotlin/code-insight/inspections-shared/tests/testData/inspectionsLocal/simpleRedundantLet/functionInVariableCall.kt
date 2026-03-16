// PROBLEM: none
// WITH_STDLIB
// IGNORE_K1
// IGNORE_K2
class Foo(val bar: () -> Int)

fun bar(foo: Foo?) {
    foo?.<caret>let { it.bar() }
}