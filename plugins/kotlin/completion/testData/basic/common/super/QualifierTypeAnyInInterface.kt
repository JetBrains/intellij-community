interface X

interface I : X {
    fun foo() {
        super<<caret>
    }
}

// IGNORE_K2
// EXIST: Any
// EXIST: X
// NOTHING_ELSE