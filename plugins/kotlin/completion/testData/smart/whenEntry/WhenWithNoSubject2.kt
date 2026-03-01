fun foo(s: String) {
    when {
        s.<caret>
    }
}

// EXIST: equals
// ABSENT: else

// IGNORE_K2
