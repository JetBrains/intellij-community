// COMPILER_ARGUMENTS: -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// PROBLEM: none

enum class A {
    TEST;

    companion object {
        val entries =""
    }
}

fun main() {
    A.<caret>Companion.entries
}