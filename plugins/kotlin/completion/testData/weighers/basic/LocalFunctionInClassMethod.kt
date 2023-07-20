// FIR_COMPARISON
// FIR_IDENTICAL

class A {
    fun aa1Member() {
        fun aa2Local() {}
            aa<caret>
    }
}

// ORDER: aa2Local
// ORDER: aa1Member