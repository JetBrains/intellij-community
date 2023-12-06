// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// API_VERSION: 1.9
// WITH_STDLIB

// No special handling for removal of this import
import EnumClass.values

private enum class EnumClass

fun foo() {
    for (e in values<caret>()) { }
}
