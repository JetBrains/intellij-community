// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class EnumClass

fun <T> functionWithGenericArg(a: T) {}

fun foo() {
    functionWithGenericArg(EnumClass.values<caret>())
}