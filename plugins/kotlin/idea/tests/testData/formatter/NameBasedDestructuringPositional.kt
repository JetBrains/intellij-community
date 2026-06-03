fun test() {
    [val a, val b] = run {
        foo()
    }
}

// SET_TRUE: ALLOW_TRAILING_COMMA
