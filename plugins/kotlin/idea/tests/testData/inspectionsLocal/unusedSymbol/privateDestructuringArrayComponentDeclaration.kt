// PROBLEM: none
// SKIP_ERRORS_BEFORE
private operator fun <E> Array<E>.<caret>component6() = this[5] // Function "component6" is never used

fun destructArray() : Int {
    val (e1, e2, e3, e4, e5, e6) = arrayOf(1, 2, 3, 4, 5, 6)
    return (e1 + e2 + e3 + e4 + e5 + e6)
}
