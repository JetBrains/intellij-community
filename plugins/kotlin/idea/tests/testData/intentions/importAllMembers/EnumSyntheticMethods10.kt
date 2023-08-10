// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// IS_APPLICABLE: false

import A.*

enum class A { A1, A2 }
enum class B { B1, B2 }

fun foo() {
    A1
    A2
    entries

    <caret>B.B1
    B.B2
    B.values()
}
