// FIR_COMPARISON

fun foo() {
    emptyMap<String, Int>()
        .forEach { <caret> }
}

// ELEMENT: "entry ->"