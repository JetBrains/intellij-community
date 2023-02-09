// WITH_STDLIB
// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
fun foo() {
    loop@ for (i in 1..5) {
        (1..5).forEach {
            if (it == 2) co<caret>ntinue@loop
        }
    }
}