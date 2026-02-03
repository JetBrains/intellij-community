// PROBLEM: none
// SKIP_ERRORS_BEFORE
private operator fun <E> List<E>.<caret>component6() = this[5] // Function "component6" should not be "never used"

fun destructList(l: List<Int>) : Int {
    val (e1, e2, e3, e4, e5, e6) = l
    return (e1 + e2 + e3 + e4 + e5 + e6)
}
