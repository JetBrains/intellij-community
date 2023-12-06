// WITH_STDLIB
fun foo(list: List<String>): Boolean {
    <caret>if (list.isEmpty()) return false
    return true
}
