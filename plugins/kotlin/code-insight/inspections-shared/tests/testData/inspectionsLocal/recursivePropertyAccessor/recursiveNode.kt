// PROBLEM: none

class Node(val next: Node?) {
    val last: Node
        get() = if (next != null) next.last<caret> else this
}
