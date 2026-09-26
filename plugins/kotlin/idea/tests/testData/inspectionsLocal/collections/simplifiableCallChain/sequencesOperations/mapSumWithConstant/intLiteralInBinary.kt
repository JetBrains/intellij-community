// PROBLEM: none
// WITH_STDLIB
fun test(sequence: Sequence<Int>, i: Int) {
    sequence.map<caret> { 1 + 2 }.sum()
}