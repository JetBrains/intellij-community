// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class EnumClass

val a = EnumClass.values<caret>().toList()
