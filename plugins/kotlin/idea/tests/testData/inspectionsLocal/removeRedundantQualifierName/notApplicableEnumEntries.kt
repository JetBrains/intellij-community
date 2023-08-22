// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// PROBLEM: none

package packageName

import packageName.MyEnum.*

enum class MyEnum {
    A, B, C, D, E;
}

fun main() {
    for (value in <caret>MyEnum.entries) {
    }
}