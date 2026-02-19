// FIR_IDENTICAL
// FIR_COMPARISON

class Test {
    operator fun invoke(a: Int): Int = 5
    operator fun get(a: Int): Int = 5
}


fun some(test: Test) {
    test?.<caret>
}

// ABSENT: "[]"
// ABSENT: "()"
