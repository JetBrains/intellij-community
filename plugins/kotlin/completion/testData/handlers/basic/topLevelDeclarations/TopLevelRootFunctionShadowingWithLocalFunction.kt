fun usage() {
    fun topLevelFunction() {}

    topLevelF<caret>
}

// ELEMENT: topLevelFunction
// TAIL_TEXT: "()"