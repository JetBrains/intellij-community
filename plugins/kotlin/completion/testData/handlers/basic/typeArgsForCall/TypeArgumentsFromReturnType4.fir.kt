// FIR_COMPARISON

interface A

fun <U: A> foo(): U = f

fun <T: CharSequence> test(): T {
    return f<caret>
}

// ELEMENT: foo