class Foo<A>(a: A) {
    fun <B> foo(b: B): Foo<B> = TODO()
    fun <B, C> foo(b: B, c: C): Foo<C> = TODO()

    fun test() {}
}

fun main() {
    Foo(5).foo(5).<caret>
}

// ELEMENT: test
