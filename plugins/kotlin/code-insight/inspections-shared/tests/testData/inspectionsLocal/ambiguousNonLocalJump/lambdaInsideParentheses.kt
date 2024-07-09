// WITH_STDLIB
// IGNORE_K1
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
// DISABLE-ERRORS
fun foo() {
    for (i in 1..5) {
        (1..5).forEach({
            if (it == 2) co<caret>ntinue
        })
    }
}