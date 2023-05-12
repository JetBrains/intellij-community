// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// API_VERSION: 1.9
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