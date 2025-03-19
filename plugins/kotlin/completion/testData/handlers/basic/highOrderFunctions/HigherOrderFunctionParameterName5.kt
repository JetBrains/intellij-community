// IGNORE_K1

fun foo() {
    emptyMap<String, Int>()
        .forEach { p0, <caret> }
}

// ELEMENT: "p1"
// ITEM_TEXT: "p1"
// TAIL_TEXT: " -> "
// TYPE_TEXT: "Int"