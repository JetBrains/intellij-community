// COMPILER_ARGUMENTS: -XXLanguage:+SamConversionForKotlinFunctions

fun usage(r: Runnable) {}

fun test() {
    usage(Runnable<caret> { })
}
