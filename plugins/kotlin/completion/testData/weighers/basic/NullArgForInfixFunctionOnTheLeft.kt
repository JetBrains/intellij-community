// FIR_COMPARISON
// FIR_IDENTICAL
fun nulls() {}

fun foo() {
    nu<caret> to 10 // stdlib Tuples.kt function producing a Pair
}

// ORDER: null
// ORDER: nulls