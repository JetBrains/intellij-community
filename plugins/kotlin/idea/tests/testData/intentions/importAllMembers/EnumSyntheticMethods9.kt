// PRIORITY: HIGH
// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// AFTER-WARNING: Ambiguous access to property 'entries' is deprecated. Please, specify type of the referenced expression explicitly
// AFTER-WARNING: The expression is unused

import A.*

enum class A { A1, A2 }
enum class B { B1, B2 }

fun foo() {
    A1
    A2
    A::entries
    A.entries

    <caret>B.B1
    B.B2
    B.values()
}
