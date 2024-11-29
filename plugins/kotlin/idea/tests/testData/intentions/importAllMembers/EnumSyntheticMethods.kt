// PRIORITY: HIGH
// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB

package test

enum class MyEnum {
    A, B, C, D, E
}

fun main() {
    <caret>MyEnum.A
    MyEnum.valueOf("A")
    MyEnum.values()
    MyEnum.entries
}
