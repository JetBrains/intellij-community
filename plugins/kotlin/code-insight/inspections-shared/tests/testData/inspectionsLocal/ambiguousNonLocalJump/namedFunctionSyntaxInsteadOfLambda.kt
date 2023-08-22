// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
fun foo() {
    for (i in 1..5) {
        (1..5).forEach(fun(it: Int) {
            if (it == 2) co<caret>ntinue
        })
    }
}