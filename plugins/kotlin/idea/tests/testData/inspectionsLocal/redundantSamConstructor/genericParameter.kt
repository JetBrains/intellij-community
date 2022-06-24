// COMPILER_ARGUMENTS: -XXLanguage:+SamConversionForKotlinFunctions
// PROBLEM: none

fun <T> test(t: T): T = t

fun usage() {
    test(Runnable<caret> {})
}