// COMPILER_ARGUMENTS: -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class SomeEnum {
    <caret>UNUSED;

    companion object {
        val entries = ""

        fun main() {
            entries
        }
    }
}
