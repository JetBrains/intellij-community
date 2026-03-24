interface Foo

fun test(flag: Boolean): Foo {
    return when (flag) {
        true -> <caret>
    }
}

// ELEMENT: object