// FIR_COMPARISON
// FIR_IDENTICAL

fun aaX() {}
fun aaaY() {}

fun test() {
    aa<caret>
}

// ORDER: aaX
// ORDER: aaaY