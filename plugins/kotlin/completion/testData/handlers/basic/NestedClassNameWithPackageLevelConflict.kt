package test

class Main {
    class Bar

    fun test(b: B<caret>)
}

// IGNORE_K2
// ELEMENT: Bar
// TAIL_TEXT: " (foo)"