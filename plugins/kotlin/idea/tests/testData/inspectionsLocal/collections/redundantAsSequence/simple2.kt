// WITH_STDLIB
fun test(set: Set<String>) {
    set.<caret>asSequence().any { it.isBlank() }
}