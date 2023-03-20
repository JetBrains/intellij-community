fun check(i: () -> Int): Int {
    a()
        .d{}.check { 3 }<caret>.dec()
    return 4
}

// SET_FALSE: CONTINUATION_INDENT_FOR_CHAINED_CALLS
// IGNORE_INV_FORMATTER