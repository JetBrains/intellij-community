// WITH_STDLIB
// IS_APPLICABLE: false
// PROBLEM: none

fun <T> Iterable<T>.intersect(other: Iterable<T>): Set<T> = other.toSet()

fun foo(a: Iterable<Int>, b: Iterable<Int>) {
    val c = a.intersect(<caret>b)
}
