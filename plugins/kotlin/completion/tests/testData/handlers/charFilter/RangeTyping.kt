fun foo(start: Int) {
    val range = start.<caret>
}

// INVOCATION_COUNT: 0
// ELEMENT: *
// CHAR: .