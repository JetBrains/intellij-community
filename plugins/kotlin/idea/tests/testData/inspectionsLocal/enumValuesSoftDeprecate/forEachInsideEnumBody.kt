// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// API_VERSION: 1.9
// WITH_STDLIB
enum class EnumClass {
    ONE;

    init {
        values<caret>().forEach {}
    }
}

