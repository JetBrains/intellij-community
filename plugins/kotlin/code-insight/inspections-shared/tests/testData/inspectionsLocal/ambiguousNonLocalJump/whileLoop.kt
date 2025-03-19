// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
// DISABLE_ERRORS
fun foo() {
    while (true) {
        (1..5).forEach {
            if (it == 2) bre<caret>ak
        }
    }
}
