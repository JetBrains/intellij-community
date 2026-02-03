// WITH_STDLIB
fun test(sequence: Sequence<Int>) {
    sequence.<caret>map(fun(it: Int): Int {
        return 1
    }).sum()
}