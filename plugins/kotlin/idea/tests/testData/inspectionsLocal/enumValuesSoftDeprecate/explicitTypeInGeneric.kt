// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class EnumClass

fun <T> genericFunction(a: T) {}

fun foo() {
    genericFunction<Array<*>>(EnumClass.values<caret>())
}