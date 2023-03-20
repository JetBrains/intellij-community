// "Create extension function 'Int.foo'" "true"
// WITH_STDLIB

class A<T>(val n: T)

fun test() {
    val a: A<Int> = 2.<caret>foo(A(1))
}