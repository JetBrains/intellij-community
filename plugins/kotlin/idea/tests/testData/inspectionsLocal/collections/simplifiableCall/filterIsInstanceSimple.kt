// WITH_STDLIB
fun test(list: List<Any>) {
    list.<caret>filter { it is String }
}