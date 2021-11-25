// PROBLEM: none
// WITH_STDLIB
fun test(list: List<Int>) {
    list.<caret>asSequence().runningReduce { acc, i -> acc + i }.take(10).last()
}