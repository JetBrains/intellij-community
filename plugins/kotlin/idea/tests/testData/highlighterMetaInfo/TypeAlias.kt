// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
typealias Predicate<T> = (T) -> Boolean
fun baz(p: Predicate<Int>) = p(42)
