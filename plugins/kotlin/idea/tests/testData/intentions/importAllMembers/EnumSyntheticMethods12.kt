// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// IS_APPLICABLE: false
enum class MyEnum {
    A, B, C, D, E
}

fun main() {
    MyEnum.A
    MyEnum.valueOf("A")
    <caret>MyEnum.entries
}
