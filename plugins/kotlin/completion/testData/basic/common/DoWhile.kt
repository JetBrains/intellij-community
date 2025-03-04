// IGNORE_K2

fun foo() {
    do {
        val shouldContinue = Random.nextBoolean()
    } while (shouldContinu<caret>)
}

// EXIST: shouldContinue