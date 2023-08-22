// FIR_IDENTICAL
// FIR_COMPARISON
import lib.JavaClass

fun test() = JavaClass().foo(<caret>)

// ABSENT: "p0 ="
// ABSENT: "paramName ="
