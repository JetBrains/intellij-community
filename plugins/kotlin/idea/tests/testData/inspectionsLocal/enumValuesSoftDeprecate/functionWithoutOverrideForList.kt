// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class EnumClass

fun funWithoutOverride(arg: Array<*>) {}

fun foo() {
    funWithoutOverride(EnumClass.values<caret>())
}