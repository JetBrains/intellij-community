// WITH_STDLIB
fun test(list: List<String>) {
    list.<caret>asSequence().all { it.isBlank() }
}