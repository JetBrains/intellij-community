// PROBLEM: none
class Comment {
    fun isEmpty(): Boolean = false
}

fun test1(c: Comment?) {
    if (<caret>c == null || c.isEmpty()) {}
}