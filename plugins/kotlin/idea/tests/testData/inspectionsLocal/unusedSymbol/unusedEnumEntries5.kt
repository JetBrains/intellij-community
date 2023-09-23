// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class SomeEnum {
    <caret>UNUSED;

    companion object {
        fun entries(a: Int) {
            return
        }
    }
}

fun main() {
    SomeEnum.entries(1)
}
