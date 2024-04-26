enum class E {
    A,
    B,
    C
}

fun foo(e: E) {
    when (e) {
        E.A -> {}

        E.<caret>
    }
}

// IGNORE_K2
// ORDER: B
// ORDER: C
// ORDER: valueOf
// ORDER: A
// ORDER: values
