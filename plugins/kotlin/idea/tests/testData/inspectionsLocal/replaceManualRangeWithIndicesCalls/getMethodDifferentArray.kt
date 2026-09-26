// WITH_STDLIB
// FIX: Replace with indices
fun test(args: Array<String>, other: Array<String>) {
    for (index in 0..<caret>args.size - 1) {
        val a = args.get(index)
        val b = other.get(index)
    }
}