// FIR_COMPARISON

fun foo() {
    emptyMap<String, Int>()
        .forEach { <caret> }
}

// ELEMENT: "p0"
// ITEM_TEXT: "p0, p1"
// TAIL_TEXT: " -> "
// TYPE_TEXT: "(String, Int)"