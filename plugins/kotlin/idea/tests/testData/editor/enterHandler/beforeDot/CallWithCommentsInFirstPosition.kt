fun check(i: () -> Int) {
       i()/*123*/ /*
    */<caret>.dec()
}

// SET_FALSE: CONTINUATION_INDENT_FOR_CHAINED_CALLS