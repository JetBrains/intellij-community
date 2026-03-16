// WITH_STDLIB
// FIX: Replace with loop over elements
fun test(args: Array<String>) {
    for (index in 0..ar<caret>gs.size - 1) {
        val out = args[index]
    }
}
