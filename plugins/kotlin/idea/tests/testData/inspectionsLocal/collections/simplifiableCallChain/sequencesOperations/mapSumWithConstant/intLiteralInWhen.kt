// PROBLEM: none
// WITH_STDLIB
fun test(sequence: Sequence<Int>, i: Int) {
    sequence.map<caret> {
        when (i) {
            1 -> 1
            2 -> 2
            else -> 3
        }
    }.sum()
}