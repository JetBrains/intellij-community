fun test() {
    if (true &&
        <caret>)
}

// SET_FALSE: CONTINUATION_INDENT_IN_IF_CONDITIONS
// IGNORE_FORMATTER