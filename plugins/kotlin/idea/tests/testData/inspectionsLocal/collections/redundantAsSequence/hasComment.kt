// WITH_STDLIB
fun test(list: List<String>) {
    list/*comment*/.<caret>asSequence().last()
}