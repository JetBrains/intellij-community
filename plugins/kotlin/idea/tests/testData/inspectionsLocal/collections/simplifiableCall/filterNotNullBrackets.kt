// WITH_STDLIB
fun test(list: List<String?>) {
    list.<caret>filter({ it != null })
}