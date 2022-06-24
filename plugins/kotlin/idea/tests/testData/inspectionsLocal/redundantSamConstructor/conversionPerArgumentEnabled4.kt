// COMPILER_ARGUMENTS: -XXLanguage:+SamConversionForKotlinFunctions -XXLanguage:+FunctionalInterfaceConversion -XXLanguage:+SamConversionPerArgument

fun test(r1: Runnable, r2: Runnable) {}

fun usage() {
    test(Runnable { return@Runnable }, Runnable<caret> {})
}
