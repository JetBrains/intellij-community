// WITH_STDLIB
// FIX: Convert argument to 'Set'

fun <T> f(a: Iterable<T>, b: Iterable<T>) = a intersect <caret>b
