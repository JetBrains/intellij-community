// FIR_COMPARISON
// FIR_IDENTICAL

fun foo() {
    do {
        val shouldContinue = Random.nextBoolean()
    } while (shouldContinu<caret>)
}

// EXIST: shouldContinue