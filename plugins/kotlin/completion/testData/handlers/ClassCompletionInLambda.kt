// FIR_COMPARISON
// FIR_IDENTICAL
fun foo(list: List<String>) {
    list.map { Stri<caret> }
}

// ELEMENT: String
// TAIL_TEXT: " (kotlin)"