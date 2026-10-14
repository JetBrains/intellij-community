sealed interface Foo {
    data object Bar : Foo
}

fun foo(f: Foo) {
    if (f == <caret>) {
    }
}

// ORDER: f
// ORDER: Bar
