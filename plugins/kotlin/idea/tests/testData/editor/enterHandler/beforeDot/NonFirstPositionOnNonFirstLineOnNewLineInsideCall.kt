fun check() {
    println(
        aa
            .abc<caret>.aaa
    )
}

// SET_FALSE: CONTINUATION_INDENT_FOR_CHAINED_CALLS
// IGNORE_INV_FORMATTER