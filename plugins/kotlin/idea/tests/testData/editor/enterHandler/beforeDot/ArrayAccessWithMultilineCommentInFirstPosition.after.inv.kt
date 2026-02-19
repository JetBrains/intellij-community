fun check(i: List<Int>) {
    i[1]/*12
            3*/
            <caret>.dec()
}

// SET_FALSE: CONTINUATION_INDENT_FOR_CHAINED_CALLS