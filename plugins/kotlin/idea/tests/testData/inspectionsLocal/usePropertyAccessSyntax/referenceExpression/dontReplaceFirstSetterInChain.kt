// PROBLEM: none
// ERROR: Unresolved reference: setValue
// ERROR: Unresolved reference: with
// K2_ERROR: Unresolved reference 'setValue'.

import java.io.File

fun testDontReplaceFirstSetterInChain(file: File) {
    with(file) {
        <caret>setValue(1).doSomething()
    }
}

fun Int.doSomething() {}