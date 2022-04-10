// FIR_IDENTICAL
// FIR_COMPARISON
fun test(p: Pair<Any, Any>) {
    if (p.first is List<*>) {
        p.first.siz<caret>
    }
}

// ELEMENT: length

