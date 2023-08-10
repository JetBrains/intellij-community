// FIR_COMPARISON
// FIR_IDENTICAL
fun returnFun(): String = ""

fun usage(a: Int?): Int {
    a to re<caret>
    return 10
}

// ORDER: returnFun
// ORDER: return
