// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class EnumClass

@ExperimentalStdlibApi
fun foo() {
    for (el in EnumClass.values<caret>()) { }
}