// FIR_COMPARISON

package test

fun test() {
    class Bar

    val bar = Bar<caret>
}

// ELEMENT: Bar
// TAIL_TEXT: " (foo)"