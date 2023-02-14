fun check() {
    val a = a
        .c<caret>.f
}

// SET_FALSE: CONTINUATION_INDENT_FOR_CHAINED_CALLS
// IGNORE_INV_FORMATTER