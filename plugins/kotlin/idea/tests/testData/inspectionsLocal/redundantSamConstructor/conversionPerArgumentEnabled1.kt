// COMPILER_ARGUMENTS: -XXLanguage:+FunctionalInterfaceConversion

fun usage(r1: Runnable) {
    Test.test(r1, Runnable<caret> {})
}
