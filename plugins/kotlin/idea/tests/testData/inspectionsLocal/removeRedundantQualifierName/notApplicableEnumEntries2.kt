// COMPILER_ARGUMENTS: -XXLanguage:-EnumEntries
// PROBLEM: none

package packageName

import packageName.MyEnum.*

enum class MyEnum {
    A, B, C, D, E;

    companion object {
        val entries = ""
    }
}

fun main() {
    <caret>MyEnum.entries
}