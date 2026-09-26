interface Foo

fun test(maybeNull: Foo?): Foo {
    return maybeNull ?: <caret>
}

// ELEMENT: object