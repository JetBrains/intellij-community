// FIR_IDENTICAL
// FIR_COMPARISON
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
fun foo() {
    myFor@
    for (i in 1..10) {
        while (x()) {
            run {
                cont<caret>
            }
        }
    }
}

fun <T> run(block: () -> T): T = block()

// NUMBER: 0