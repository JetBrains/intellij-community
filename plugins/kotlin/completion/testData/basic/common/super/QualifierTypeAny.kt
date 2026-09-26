interface I

class B : I {
    fun foo() {
        super<<caret>
    }
}

// IGNORE_K2
// EXIST: Any
// EXIST: I
// NOTHING_ELSE