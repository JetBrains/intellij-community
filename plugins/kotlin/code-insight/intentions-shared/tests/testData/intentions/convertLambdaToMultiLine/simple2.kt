// WITH_STDLIB
fun test(list: List<String>) {
    list.forEach { <caret>println(it) }
}