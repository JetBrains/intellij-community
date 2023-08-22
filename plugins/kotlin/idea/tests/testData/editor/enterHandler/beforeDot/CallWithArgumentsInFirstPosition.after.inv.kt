fun check(i: () -> Int) {
    i(33, "abc ${i()} c", {})
            <caret>.dec()
}

// SET_FALSE: CONTINUATION_INDENT_FOR_CHAINED_CALLS