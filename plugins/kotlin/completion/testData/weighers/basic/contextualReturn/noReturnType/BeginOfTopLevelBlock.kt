// FIR_COMPARISON
// FIR_IDENTICAL
fun returnFun() {}

fun usage() {
    re<caret>
    return
}

// ORDER: returnFun
// ORDER: return
