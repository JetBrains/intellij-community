// IGNORE_K1

fun foo() {
    Pair(42, "").let { first, <caret> }
}

// INVOCATION_COUNT: 0
// NOTHING_ELSE