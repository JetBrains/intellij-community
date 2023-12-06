// FIR_IDENTICAL
fun returnFun() {}

fun usage(a: Int?): Int {
    a ?: re<caret>
    return 10
}

// IGNORE_K2
// ORDER: return
// ORDER: returnFun
