// COMPILER_ARGUMENTS: -XXLanguage:-EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// PROBLEM: none
enum class EnumClass

fun foo() {
    EnumClass.values<caret>()
}