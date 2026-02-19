fun foo(b: Boolean?) {
    when (b) {
        <caret>
    }
}

// EXIST: true
// EXIST: false
// EXIST: null

// IGNORE_K2
