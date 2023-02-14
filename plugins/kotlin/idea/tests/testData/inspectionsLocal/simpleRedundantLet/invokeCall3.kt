// WITH_STDLIB
class A {
    operator fun invoke() {}
}

fun foo(a: A) {
    a.<caret>let { b -> b.invoke() }
}