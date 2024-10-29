package pack

fun usage() {
    topLevelF<caret>
}

// IGNORE_K2
// ELEMENT: topLevelFunction
// TAIL_TEXT: "() (<root>)"