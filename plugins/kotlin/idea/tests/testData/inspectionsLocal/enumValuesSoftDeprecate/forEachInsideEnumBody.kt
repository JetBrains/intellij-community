// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class EnumClass {
    ONE;

    init {
        values<caret>().forEach {}
    }
}

// IGNORE_FIR