// FIR_IDENTICAL
// FIR_COMPARISON

// WITH_STDLIB

fun buildTemplates() {
    val kotlin = 42
    printl<caret>
}

// ELEMENT: println
// TAIL_TEXT: "() (kotlin.io)"