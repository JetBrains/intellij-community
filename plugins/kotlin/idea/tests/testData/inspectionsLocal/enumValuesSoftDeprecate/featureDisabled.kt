// COMPILER_ARGUMENTS: -XXLanguage:-EnumEntries
// API_VERSION: 1.9
// PROBLEM: none
enum class EnumClass

fun foo() {
    EnumClass.values<caret>()
}