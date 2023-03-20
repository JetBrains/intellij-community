// COMPILER_ARGUMENTS: -XXLanguage:-EnumEntries
// PROBLEM: none
enum class EnumClass

fun foo() {
    EnumClass.values<caret>()
}