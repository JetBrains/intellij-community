// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class EnumClass {
    ;
    init {
        val v = values<caret>()
    }
}

// IGNORE_FIR