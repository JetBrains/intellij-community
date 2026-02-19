// WITH_STDLIB
// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
// ERROR: The feature "break continue in inline lambdas" is disabled
// K2_ERROR:
fun foo() {
    for (i in 1..5) {
        run {
            if (i == 2) co<caret>ntinue
        }
    }
}
