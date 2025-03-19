// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// PROBLEM: none
enum class SomeEnum {
    <caret>USED
}

fun test() {
    SomeEnum.values().forEach { it }
}