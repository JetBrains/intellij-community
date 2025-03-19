// FIR_IDENTICAL
fun returnFun() {}

fun usage(a: Int?) {
    a ?: re<caret>
    return
}

// IGNORE_K2
// ORDER: return
// ORDER: returnFun
