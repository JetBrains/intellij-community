fun usage(
    // Parameter with the same function name in another package
    topLevelFunction: () -> Unit
) {
    topLevelF<caret>
}

// IGNORE_K2
// ELEMENT: topLevelFunction
// TAIL_TEXT: "() (pack)"