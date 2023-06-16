// FIR_COMPARISON
// FIR_IDENTICAL
package pack

import pack.A.xxx

object A {
    fun xxx() {}
}

fun xxx() {}

fun test() {
    xx<caret>
}

// ELEMENT: xxx
// TAIL_TEXT: "() (pack)"