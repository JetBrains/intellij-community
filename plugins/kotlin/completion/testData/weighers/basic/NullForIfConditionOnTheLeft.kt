// FIR_COMPARISON
// FIR_IDENTICAL
fun nulls() {}

fun foo() {
    if (nu<caret> == a)
}

// ORDER: null
// ORDER: nulls