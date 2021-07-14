// WITH_RUNTIME
fun test(list: List<String>) {
    val range = <caret>0.rangeTo(list.size - 1)
}
