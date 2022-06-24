// COMPILER_ARGUMENTS: -XXLanguage:+FunctionalInterfaceConversion

fun test(r1: Runnable, r2: Runnable) {}

fun usage() {
    test(Runnable { return@Runnable }, Runnable<caret> {})
}
