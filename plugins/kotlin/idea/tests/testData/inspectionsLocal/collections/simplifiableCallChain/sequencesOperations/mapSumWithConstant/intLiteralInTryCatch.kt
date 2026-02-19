// PROBLEM: none
// WITH_STDLIB
fun test(sequence: Sequence<Int>) {
    sequence.map<caret> {
        try {
            1
        } catch (e: Exception) {
            if (true) 1 else 0
        }
    }.sum()
}