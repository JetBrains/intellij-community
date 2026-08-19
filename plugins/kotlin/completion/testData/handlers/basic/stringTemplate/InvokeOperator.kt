// FIR_IDENTICAL
// FIR_COMPARISON
fun foo(): String {
    val lambda = { name: String -> "Hello $name!" }
    return "$lambda.<caret>"
}

// ELEMENT: ()
