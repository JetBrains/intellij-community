// FIR_IDENTICAL
// FIR_COMPARISON
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
fun foo() {
    myFor@
    for (i in 1..10) {
        while (x()) {
            run {
                br<caret>
            }
        }
    }
}

inline fun <T> run(block: () -> T): T = block()

// NUMBER: 2
// EXIST: {"lookupString":"break","attributes":"bold","allLookupStrings":"break","itemText":"break"}
// EXIST: {"lookupString":"break@myFor","tailText":"@myFor","attributes":"bold","allLookupStrings":"break@myFor","itemText":"break"}