// FIR_COMPARISON
fun test(p: Pair<Any, Any>) {
    if (p.first is String) {
        p.first.len<caret>
    }
}

// ELEMENT: length

