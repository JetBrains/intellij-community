fun <T> foo() {}

fun test() {
    foo<FooB<caret>()
}

// ELEMENT: FooBar