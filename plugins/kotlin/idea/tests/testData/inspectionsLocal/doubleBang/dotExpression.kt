// FIX: "Replace with '?: error(…)'"
class Node {
    val next: Node? = null
}

fun test(a: Node?) {
    (a ?: error("'a' must not be null")).next!!.next!!.next<caret>!!
}