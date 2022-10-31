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

inline fun <T> run(block: () -> T): T = block()

// NUMBER: 2
// EXIST: {"lookupString":"continue","attributes":"bold","allLookupStrings":"continue","itemText":"continue"}
// EXIST: {"lookupString":"continue@myFor","tailText":"@myFor","attributes":"bold","allLookupStrings":"continue@myFor","itemText":"continue"}