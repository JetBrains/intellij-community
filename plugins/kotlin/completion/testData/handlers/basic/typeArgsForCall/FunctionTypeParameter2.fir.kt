// FIR_COMPARISON
fun f(list: List<String>) {
    list.<caret>
}

// ELEMENT: map
// TAIL_TEXT: " { transform: (String) -> R } for Iterable<T> in kotlin.collections"