interface Foo

class Bar {
    val prop: Foo by lazy {
        <caret>
    }
}

// ELEMENT: object
