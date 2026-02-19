// FIR_COMPARISON

fun foo() {
    emptyMap<String, Int>()
        .forEach { <caret> }
}

// ELEMENT: "key"
// ITEM_TEXT: "(key, value)"
// TAIL_TEXT: " -> "
// TYPE_TEXT: "(String, Int)"