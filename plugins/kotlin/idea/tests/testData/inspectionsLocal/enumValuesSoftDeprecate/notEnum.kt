// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
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