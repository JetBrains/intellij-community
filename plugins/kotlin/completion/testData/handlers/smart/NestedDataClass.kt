class Outer {
    private data class Nested(val v: Int)

    fun foo(): Nested {
        return <caret>
    }
}

// ELEMENT: Nested

// IGNORE_K2
