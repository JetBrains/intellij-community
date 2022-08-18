fun check(i: () -> Int) {
    i()
        <caret>.dec()
}

// SET_FALSE: CONTINUATION_INDENT_FOR_CHAINED_CALLS