// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// API_VERSION: 1.9
// WITH_STDLIB
enum class EnumClass

fun funWithOverride(arg: Array<*>) {}
fun funWithOverride(arg: List<*>) {}

fun foo() {
    funWithOverride(EnumClass.values<caret>())
}