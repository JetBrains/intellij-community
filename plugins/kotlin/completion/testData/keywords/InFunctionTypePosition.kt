// FIR_IDENTICAL
// FIR_COMPARISON
fun foo() {
    val test : <caret>
}

// EXIST: suspend
// EXIST: context
// NOTHING_ELSE
