// FIR_COMPARISON
// FIR_IDENTICAL
fun returnFun(): Int = 10

fun usage(): Int {
    if (true) {
        re<caret>
        return 20
    }

    return 10
}

// ORDER: returnFun
// return should appear after, but does not have to be the very next element
