fun check() {
    return ref.call()
        <caret>.call2()
}

// SET_FALSE: CONTINUATION_INDENT_FOR_CHAINED_CALLS