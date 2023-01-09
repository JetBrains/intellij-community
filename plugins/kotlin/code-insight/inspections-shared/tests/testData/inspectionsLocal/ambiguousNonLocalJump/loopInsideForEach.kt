// WITH_STDLIB
// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
fun foo() {
    (1..5).forEach {
        for (i in 1..5) {
            if (it == 2) co<caret>ntinue
        }
    }
}