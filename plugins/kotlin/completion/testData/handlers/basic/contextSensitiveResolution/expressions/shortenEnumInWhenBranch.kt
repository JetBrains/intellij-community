// FIR_COMPARISON
// FIR_IDENTICAL
package test

enum class Color { RED, GREEN, BLUE }

fun test(c: Color) {
    when (c) {
        RE<caret>
    }
}

// ELEMENT: RED

// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
