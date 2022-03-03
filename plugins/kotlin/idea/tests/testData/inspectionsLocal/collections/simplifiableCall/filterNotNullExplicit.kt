// WITH_STDLIB
fun test(list: List<String?>) {
    list.<caret>filter { arg -> arg != null }
}