// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class EnumClass

// No special handling for this case
val a = EnumClass.values<caret>().asList()
