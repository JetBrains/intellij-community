// COMPILER_ARGUMENTS: -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// PROBLEM: none
import E.*

enum class E {
    <caret>X
}

fun test() {
    entries
}
