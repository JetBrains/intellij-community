// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB

enum class A {
    TEST;

    companion object {
        fun entries(name: String) {
        }
    }
}

fun main() {
    A.<caret>Companion.entries("ds")
}