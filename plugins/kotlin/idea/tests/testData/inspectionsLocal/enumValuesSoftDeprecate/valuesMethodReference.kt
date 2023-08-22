// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// API_VERSION: 1.9
// PROBLEM: none
enum class EnumClass

fun foo() {
    // No special handling for method references
    EnumClass::v<caret>alues
}