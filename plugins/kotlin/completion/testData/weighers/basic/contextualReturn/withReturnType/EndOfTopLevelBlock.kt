// FIR_COMPARISON
// FIR_IDENTICAL
fun returnFun(): Int = 10

fun usage(): Int {
    re<caret>
}

// ORDER: return
// ORDER: returnFun