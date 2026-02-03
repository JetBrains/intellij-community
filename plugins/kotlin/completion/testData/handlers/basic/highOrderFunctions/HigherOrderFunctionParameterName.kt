// FIR_COMPARISON

fun foo(
    block: (reference: String) -> Unit,
) {
}

fun bar() {
    foo { <caret> }
}

// ELEMENT: "reference ->"