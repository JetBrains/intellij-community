fun nulls() {}

fun foo() {
    10 to nu<caret> // stdlib Tuples.kt function producing a Pair
}

// IGNORE_K2
// ORDER: null
// ORDER: nulls