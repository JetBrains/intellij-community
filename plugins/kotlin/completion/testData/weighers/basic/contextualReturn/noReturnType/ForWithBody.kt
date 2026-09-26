// FIR_COMPARISON
// FIR_IDENTICAL
fun returnFun() {}

fun usage() {
    for (i in 1..10) {
        re<caret>
    }
}

// ORDER: returnFun
// return should appear after, but does not have to be the very next element
