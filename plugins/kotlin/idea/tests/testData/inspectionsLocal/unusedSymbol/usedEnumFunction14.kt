// COMPILER_ARGUMENTS: -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// PROBLEM: none
enum class SomeEnum {
    <caret>USED;

    companion object {
        fun test() {
            val f = entries
        }
    }
}
