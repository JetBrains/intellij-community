// PROBLEM: none

import java.io.File

fun testDontReplaceFirstSetterInChain(file: File) {
    with(file) {
        <caret>setValue(1).doSomething()
    }
}

fun Int.doSomething() {}