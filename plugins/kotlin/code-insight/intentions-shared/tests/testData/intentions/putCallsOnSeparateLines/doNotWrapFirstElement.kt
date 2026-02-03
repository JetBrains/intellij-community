// DISABLE_ERRORS
fun calculate(): Int {
    return call1().call2().call3()<caret>.call5().ref6.call7()
}

// SET_TRUE: WRAP_FIRST_METHOD_IN_CALL_CHAIN
