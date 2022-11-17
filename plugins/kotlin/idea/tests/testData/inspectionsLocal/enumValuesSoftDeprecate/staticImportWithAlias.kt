// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// PROBLEM: none

import EnumClass.values as valuesAlias

private enum class EnumClass

fun foo() {
    // Import aliases not handled
    valuesAlias<caret>()
}
