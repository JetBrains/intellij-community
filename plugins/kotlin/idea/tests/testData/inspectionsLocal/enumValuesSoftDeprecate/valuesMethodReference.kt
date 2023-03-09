// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// PROBLEM: none
enum class EnumClass

fun foo() {
    // No special handling for method references
    EnumClass::v<caret>alues
}