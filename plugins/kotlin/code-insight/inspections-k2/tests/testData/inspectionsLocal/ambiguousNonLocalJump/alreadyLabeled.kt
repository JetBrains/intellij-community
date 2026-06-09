// WITH_STDLIB
// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
// ERROR: The feature "break continue in inline lambdas" is disabled
// K2_ERROR:
fun foo() {
    loop@ for (i in 1..5) {
        (1..5).forEach {
            if (it == 2) co<caret>ntinue@loop
        }
    }
}