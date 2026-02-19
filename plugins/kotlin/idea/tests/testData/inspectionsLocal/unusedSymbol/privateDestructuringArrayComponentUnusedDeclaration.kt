// IGNORE_K1
private operator fun <E> Array<E>.component6():E = this[5]
private operator fun <E> Array<E>.<caret>component7():E = this[5]

fun t() :Int {
    val (e1, e2, e3, e4, e5, e6) = arrayOf(1, 2, 3, 4, 5, 6)
    return (e1 + e2 + e3 + e4 + e5 + e6)
}
