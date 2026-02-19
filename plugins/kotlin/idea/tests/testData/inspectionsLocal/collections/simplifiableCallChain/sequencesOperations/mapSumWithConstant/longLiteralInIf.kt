// WITH_STDLIB
fun test(sequence: Sequence<Int>, i: Int) {
    sequence.map<caret> { if (i == 1) 1 else if (i == 2) 2 else 3L }.sum()
}