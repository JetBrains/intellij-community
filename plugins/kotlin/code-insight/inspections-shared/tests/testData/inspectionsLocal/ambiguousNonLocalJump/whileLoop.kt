// WITH_STDLIB
// IGNORE_K1
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
// DISABLE-ERRORS
fun foo() {
    while (true) {
        (1..5).forEach {
            if (it == 2) bre<caret>ak
        }
    }
}
