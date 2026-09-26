// PROBLEM: none
// WITH_STDLIB
fun test(sequence: Sequence<Int>) {
    sequence.map<caret> { 1 }.sum()
}