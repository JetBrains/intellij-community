// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class EnumClass {
    ;
    init {
        for (e in values<caret>()) {}
    }
}

// IGNORE_FIR