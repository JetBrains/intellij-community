enum class Foo {
    A,
    B
}

fun test(f: Foo) {
    val result = when (f) {
        Foo.A -> TODO()
        Foo.B -> TODO()
    }
}
