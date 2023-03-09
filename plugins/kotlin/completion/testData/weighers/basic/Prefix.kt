// FIR_COMPARISON
// FIR_IDENTICAL

fun shouldCompleteTopLevelCallablesFromIndex() = true

fun foo(statement: String) {
    if (st<caret>)
}

// ORDER: statement
// ORDER: shouldCompleteTopLevelCallablesFromIndex
