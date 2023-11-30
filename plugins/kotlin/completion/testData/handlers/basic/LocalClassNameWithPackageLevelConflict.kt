package test

class Main {
    fun test() {
        class Bar

        val b = B<caret>
    }
}

// IGNORE_K2
// ELEMENT: Bar
// TAIL_TEXT: " (foo)"