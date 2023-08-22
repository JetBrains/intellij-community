// FIR_COMPARISON
// FIR_IDENTICAL
fun returnFun() {}

fun usage(a: Int) {
    when (a) {
        10 -> re<caret>
    }
    return
}

// ORDER: return
// ORDER: returnFun
