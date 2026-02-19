// FIR_COMPARISON
// FIR_IDENTICAL
public inline fun <reified T> Iterable<*>.foo(): String { }

fun f(list: List<Any>): String {
    return list.<caret>
}

// ELEMENT: foo
