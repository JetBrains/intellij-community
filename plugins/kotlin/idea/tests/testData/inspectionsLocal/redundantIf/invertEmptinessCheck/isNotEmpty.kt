// WITH_STDLIB
fun foo(list: List<String>): Boolean {
    <caret>if (list.isNotEmpty()) return false
    return true
}
