fun foo() {
    outer@ for (i in 1..5) {
        for (j in 1..i) {
            if (i + j > 5) {
                break@<caret>
            }
        }
    }
}

// INVOCATION_COUNT: 0
// EXIST: break@outer