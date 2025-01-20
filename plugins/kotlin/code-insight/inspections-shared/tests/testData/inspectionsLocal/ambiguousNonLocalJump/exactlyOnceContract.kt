// WITH_STDLIB
// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
// ERROR: The feature "break continue in inline lambdas" is only available since language version 2.2
// K2_ERROR:
fun foo() {
    for (i in 1..5) {
        run {
            if (i == 2) co<caret>ntinue
        }
    }
}
