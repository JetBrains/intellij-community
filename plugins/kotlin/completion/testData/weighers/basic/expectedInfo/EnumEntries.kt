enum class EE {
    A,
    B
}

fun foo(): EE {
    return E<caret>
}

// IGNORE_K2
// ORDER: A
// ORDER: B
// ORDER: valueOf
// ORDER: EE
