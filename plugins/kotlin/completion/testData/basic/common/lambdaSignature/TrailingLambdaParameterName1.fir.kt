// FIR_COMPARISON

fun <T> T.foo(block: (reference: T) -> Unit) {
    block(this)
}

fun bar() {
    42.foo { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "reference", tailText: " -> ", allLookupStrings: "reference", typeText: "Int" }
// EXIST: bar, null
