// WITH_STDLIB
fun test() {
    val xs = sequenceOf(1, 2, 3).<caret>asSequence().map { it + 1 }
}
