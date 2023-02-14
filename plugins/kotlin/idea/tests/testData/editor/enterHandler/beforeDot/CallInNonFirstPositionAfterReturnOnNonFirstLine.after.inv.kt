fun check() {
    return call1()
        .call()
        <caret>.call2()
}

// SET_FALSE: CONTINUATION_INDENT_FOR_CHAINED_CALLS
// IGNORE_INV_FORMATTER