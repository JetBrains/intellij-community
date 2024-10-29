fun usage(
    // Parameter with the same function name in another package
    topLevelFunction: () -> Unit
) {
    topLevelF<caret>
}

// ELEMENT: topLevelFunction
// TAIL_TEXT: ""