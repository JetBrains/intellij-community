// COMPILER_ARGUMENTS: -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// PROBLEM: none
package foo.bar.baz

import foo.bar.baz.E.*

enum class E {
    <caret>X
}

fun test() {
    entries
}