// FIR_COMPARISON
// FIR_IDENTICAL
fun returnFun() {}

fun usage() {
    re<caret>
    return
}

// ORDER: returnFun
// return should appear after, but does not have to be the very next element
