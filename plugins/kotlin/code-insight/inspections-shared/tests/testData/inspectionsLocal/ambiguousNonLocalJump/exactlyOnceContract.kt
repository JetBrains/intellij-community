// WITH_STDLIB
// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
fun foo() {
    for (i in 1..5) {
        run {
            if (i == 2) co<caret>ntinue
        }
    }
}
