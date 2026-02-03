class A {
    object O {
        class Inner {}

        val v: Inner = <caret>
    }
}

// ELEMENT: Inner

// IGNORE_K2
