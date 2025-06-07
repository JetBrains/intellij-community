// COMPILER_ARGUMENTS: -XXLanguage:+FunctionalInterfaceConversion

fun test(r1: Runnable, r2: Runnable) {}

fun usage(r1: Runnable) {
    test(r1, Runnable<caret> {})
}
