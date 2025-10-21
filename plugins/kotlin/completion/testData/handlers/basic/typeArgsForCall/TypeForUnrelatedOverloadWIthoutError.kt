class Foo<A>(a: A) {
    fun <B> foo(): Foo<B> = TODO()
    fun foo(b: Int, c: Int): Foo<A> = TODO()
    fun test(): Foo<A> = this
}

fun bar(): Foo<Int> {
    return Foo(5).foo().<caret>
}

// ELEMENT: test
