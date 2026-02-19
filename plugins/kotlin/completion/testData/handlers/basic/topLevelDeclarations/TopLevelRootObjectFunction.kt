package pack

fun usage() {
    functionFrom<caret>
}

// IGNORE_K2
// ELEMENT: functionFromRootObject
// TAIL_TEXT: "() (<root>)"