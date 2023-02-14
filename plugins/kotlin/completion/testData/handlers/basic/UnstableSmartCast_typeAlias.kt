// FIR_IDENTICAL
// FIR_COMPARISON
typealias MyList<T> = List<T>

fun test(p: Pair<Any, Any>) {
    if (p.first is MyList<*>) {
        p.first.siz<caret>
    }
}

// ELEMENT: length

