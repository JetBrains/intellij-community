// PROBLEM: none
package foo.bar.baz

import foo.bar.baz.E.valueOf

enum class E {
    <caret>X
}

fun test() {
    valueOf("")
}
