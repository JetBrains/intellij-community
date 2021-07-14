// FIR_IDENTICAL
typealias Predicate<T> = (T) -> Boolean
fun baz(p: Predicate<Int>) = p(42)
