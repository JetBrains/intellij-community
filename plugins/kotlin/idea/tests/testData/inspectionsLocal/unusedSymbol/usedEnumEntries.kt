// PROBLEM: none
// COMPILER_ARGUMENTS: -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// IGNORE_K1
import kotlin.enums.enumEntries

enum class SomeEnum {
    <caret>One,
    Two,
    Three
}

@OptIn(ExperimentalStdlibApi::class)
fun test() = enumEntries<SomeEnum>()