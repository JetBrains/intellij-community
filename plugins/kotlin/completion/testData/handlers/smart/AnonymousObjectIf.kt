interface Foo

fun test(flag: Boolean): Foo {
    return if (flag) {
        <caret>
    }
}

// ELEMENT: object