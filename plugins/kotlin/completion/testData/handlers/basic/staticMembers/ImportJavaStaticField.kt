// FIR_IDENTICAL
// FIR_COMPARISON
import java.io.File.pathSeparator

fun foo() {
    pathSeparato<caret>
}

// INVOCATION_COUNT: 1
// ELEMENT_TEXT: "pathSeparator"