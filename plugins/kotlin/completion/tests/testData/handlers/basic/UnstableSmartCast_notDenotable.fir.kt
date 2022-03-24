// FIR_COMPARISON
fun test(p: Pair<Any, Any>) {
    if (p.first is String && p.first is Int) {
        p.first.len<caret>
    }
}

// ELEMENT: length
