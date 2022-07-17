package test

class Main {
    class Bar

    fun test(b: B<caret>)
}

// ELEMENT: Bar
// TAIL_TEXT: " (foo)"