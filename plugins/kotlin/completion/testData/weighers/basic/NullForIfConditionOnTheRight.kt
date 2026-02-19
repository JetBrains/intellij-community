// FIR_COMPARISON
// FIR_IDENTICAL
fun nulls() {}

fun `nul`() {}

fun foo() {
    if (a == nu<caret>
}

// ORDER: null
// ORDER: nul
// ORDER: nulls
