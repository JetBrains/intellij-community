// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// API_VERSION: 1.9
// WITH_STDLIB
// PROBLEM: none

import EnumClass.values as valuesAlias

private enum class EnumClass

fun foo() {
    // Import aliases not handled
    valuesAlias<caret>()
}
