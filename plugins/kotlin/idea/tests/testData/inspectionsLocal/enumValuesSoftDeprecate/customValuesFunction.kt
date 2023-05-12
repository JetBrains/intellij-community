// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// PROBLEM: none
enum class EnumClass {
    companion object {
        fun values(p: Int): Array<EnumClass> = emptyArray()
    }
}

fun foo() {
    EnumClass.values<caret>(1)
}