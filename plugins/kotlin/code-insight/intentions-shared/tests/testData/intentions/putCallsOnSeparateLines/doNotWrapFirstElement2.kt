// DISABLE-ERRORS
fun calculate(): Int {
    return call1()<caret>.call2()
}

// SET_TRUE: WRAP_FIRST_METHOD_IN_CALL_CHAIN
