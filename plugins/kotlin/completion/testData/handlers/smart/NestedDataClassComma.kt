class Outer {
    private data class Nested(val v: Int, val v2: Int)

    fun foo(p: Int): Inner {
        return Outer.Nested(<caret>)
    }
}

// ELEMENT: p
