// IGNORE_K1

fun <T> T.foo(block: (reference: T) -> Unit) {
    block(this)
}

fun bar() {
    42.foo { i, <caret> }
}

// INVOCATION_COUNT: 0
// NOTHING_ELSE