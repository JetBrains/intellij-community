// WITH_STDLIB
const val i = 1
fun test(sequence: Sequence<Int>) {
    sequence.map<caret> { i }.sum()
}