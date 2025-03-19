// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class Main {
    <caret>K;

    enum class Test {
        A;

        fun test() {
            Test.entries
        }

    }
}
