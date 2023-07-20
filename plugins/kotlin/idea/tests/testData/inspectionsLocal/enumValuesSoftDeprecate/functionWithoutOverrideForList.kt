// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// API_VERSION: 1.9
// WITH_STDLIB
enum class EnumClass

fun funWithoutOverride(arg: Array<*>) {}

fun foo() {
    funWithoutOverride(EnumClass.values<caret>())
}