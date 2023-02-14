// FIR_IDENTICAL
// FIR_COMPARISON
fun small(first: Int, second: Int) {
}

fun test() = small(12, <caret>)

// ABSENT: "first ="
// EXIST: { lookupString:"second =", itemText:"second =", icon: "Parameter"}
