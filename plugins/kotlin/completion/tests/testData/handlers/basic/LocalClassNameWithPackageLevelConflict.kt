package test

class Main {
    fun test() {
        class Bar

        val b = B<caret>
    }
}

// ELEMENT: Bar
// TAIL_TEXT: " (foo)"