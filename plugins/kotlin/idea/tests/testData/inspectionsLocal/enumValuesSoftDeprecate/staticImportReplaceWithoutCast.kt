// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB

// No special handling for removal of this import
import EnumClass.values

private enum class EnumClass

fun foo() {
    for (e in values<caret>()) { }
}
