// FIR_COMPARISON
// IGNORE_K2

fun foo() {
    emptyMap<String, Int>()
        .forEach { <caret> }
}

// ELEMENT: "key"
// ITEM_TEXT: "(key, value)"
// TAIL_TEXT: " -> "
// TYPE_TEXT: "(String, Int)"