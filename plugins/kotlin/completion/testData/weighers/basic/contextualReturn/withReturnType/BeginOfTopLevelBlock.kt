// FIR_COMPARISON
// FIR_IDENTICAL
fun returnFun(): Int = 10

fun usage(): Int {
    re<caret>
    return 10
}

// ORDER: returnFun
// return should appear after, but does not have to be the very next element