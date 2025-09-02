// FIR_IDENTICAL

private sealed interface Foo {
    class Bar : Foo
}

private fun foo(foo: Foo) {
    when (foo) {
        is Foo.<caret>
    }
}

// EXIST: Bar