// PROBLEM: none
package foo.bar.baz

import foo.bar.baz.E.*

enum class E {
    <caret>X
}

fun test() {
    values()
}
