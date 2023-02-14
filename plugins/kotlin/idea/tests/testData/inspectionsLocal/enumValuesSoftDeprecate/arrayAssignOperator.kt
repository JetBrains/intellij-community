// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class EnumClass { VAL }

fun foo() {
    EnumClass.values<caret>()[0] = EnumClass.VAL
}