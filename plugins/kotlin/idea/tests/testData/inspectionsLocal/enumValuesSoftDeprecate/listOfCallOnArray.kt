// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class EnumClass

val a: List<Array<EnumClass>> = listOf(EnumClass.values<caret>())
