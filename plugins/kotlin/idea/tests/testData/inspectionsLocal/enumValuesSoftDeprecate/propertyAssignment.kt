// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// API_VERSION: 1.9
// WITH_STDLIB
enum class EnumClass

fun foo() {
    val a = EnumClass.values<caret>()
    for (el in a) {}
}