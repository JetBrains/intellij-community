package foo

class Some {
    class Bar
}

fun foo() {
    val some = Some.B<caret>
}

// ELEMENT: Bar
// TAIL_TEXT: " (foo.Some)"