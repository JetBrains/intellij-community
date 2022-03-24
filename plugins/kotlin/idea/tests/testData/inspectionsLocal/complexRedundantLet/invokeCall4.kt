// PROBLEM: none
// WITH_STDLIB
class A {
    operator fun invoke(a: A, i: Int) {}
}

fun foo(a: A) {
    a.<caret>let { it(it, 1) }
}