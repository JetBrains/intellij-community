// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
// DISABLE_ERRORS
// PROBLEM: Ambiguous non-local 'continue' ('for' vs 'forEach'). Use clarifying label.
fun foo() {
    for (i in 1..5) {
        (1..5).forEach {
            if (it == 2) co<caret>ntinue
        }
    }
}
