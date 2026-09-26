// FIR_COMPARISON
fun main(args: Array<String>) {
    args.filter<caret> { it != "" }
}

// ELEMENT: filterNot
// TAIL_TEXT: " { predicate: (String) -> Boolean } for Array<out T> in kotlin.collections"