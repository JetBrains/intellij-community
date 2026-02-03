// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
// DISABLE_ERRORS
fun foo() {
    for (i in 1..5) {
        (1..5).forEach(fun(it: Int) {
            if (it == 2) co<caret>ntinue
        })
    }
}