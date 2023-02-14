// PROBLEM: none
// WITH_STDLIB
class A {
    operator fun invoke() {}
}

fun foo(a: A) {
    a.<caret>let { it() }
}