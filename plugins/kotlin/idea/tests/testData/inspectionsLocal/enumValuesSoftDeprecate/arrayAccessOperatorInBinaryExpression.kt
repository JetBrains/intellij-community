// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class EnumClass { VAL }

fun foo() {
    val a = EnumClass.values<caret>()[0]
}