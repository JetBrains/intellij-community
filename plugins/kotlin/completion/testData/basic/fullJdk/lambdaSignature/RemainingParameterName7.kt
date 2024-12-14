// IGNORE_K1

fun foo() {
    Triple(42, "", 0.0).let { first, <caret> }
}

// INVOCATION_COUNT: 0
// NOTHING_ELSE