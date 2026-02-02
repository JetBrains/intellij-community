class A {
    companion object {
        class Inner {}
    }
}

val v: A.Companion.Inner = <caret>

// ELEMENT: Inner

// IGNORE_K2
