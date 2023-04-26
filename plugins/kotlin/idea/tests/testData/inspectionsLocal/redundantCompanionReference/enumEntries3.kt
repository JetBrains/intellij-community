// COMPILER_ARGUMENTS: -XXLanguage:-EnumEntries
// PROBLEM: none

// This behavior is relevant only for compilers higher than version 1.8.20, see KTIJ-25040 for details
enum class A {
    TEST;

    companion object {
        val entries = ""
    }
}

fun main() {
    A.<caret>Companion.entries
}