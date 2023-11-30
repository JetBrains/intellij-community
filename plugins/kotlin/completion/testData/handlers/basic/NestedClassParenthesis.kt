package foo

class Some {
    class Bar
}

fun foo() {
    val some = Some.B<caret>
}

// IGNORE_K2
// ELEMENT: Bar
// TAIL_TEXT: " (foo.Some)"