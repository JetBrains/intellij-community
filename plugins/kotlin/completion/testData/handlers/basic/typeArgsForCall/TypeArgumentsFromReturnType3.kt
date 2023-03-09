// FIR_COMPARISON
// FIR_IDENTICAL

fun <U: Int> foo(): U = f

fun <T: CharSequence> test(): T {
    return f<caret>
}

// ELEMENT: foo