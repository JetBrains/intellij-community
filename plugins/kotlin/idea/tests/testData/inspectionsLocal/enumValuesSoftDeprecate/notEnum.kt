// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// API_VERSION: 1.9
// WITH_STDLIB
// PROBLEM: none
class NotEnum {
    companion object {
        fun values(): Array<NotEnum> = emptyArray()
    }
}

fun foo() {
    NotEnum.values<caret>()
}