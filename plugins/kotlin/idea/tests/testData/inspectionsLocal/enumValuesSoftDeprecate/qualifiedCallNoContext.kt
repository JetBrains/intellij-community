// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class EnumClass

fun foo() {
    // No special handling for this case
    EnumClass.values<caret>()
}