// FIR_COMPARISON
class X

fun f(x: X?){}

fun g(nn: Any, np: X) {
    f(n<caret>)
}

// ORDER: np
// ORDER: null
// ORDER: nn
