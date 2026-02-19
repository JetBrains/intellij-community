// FIR_COMPARISON
// FIR_IDENTICAL
val aa3: Int = 1
var aa2: Int? = null
val aa1: Double = 1.0

fun test(): Int {
    return aa<caret>
}

// ORDER: aa3
// ORDER: aa2
// ORDER: aa1
