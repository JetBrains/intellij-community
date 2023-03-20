// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// PROBLEM: none
class Wrapper {
    class Wrapper2 {
        enum class MyEnum {
            <caret>Bar, Baz;
        }
    }
}

fun main() {
    Wrapper.Wrapper2.MyEnum.entries
}
