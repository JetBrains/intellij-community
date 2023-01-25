fun f(a: Int) {
    if (a > 0) {
        <lineMarker descr="Recursive call">f</lineMarker>(a - 1)
    }
}

class Node(val next: Node?) {
    fun lastNode(): Node = next?.<lineMarker descr="Recursive call">lastNode</lineMarker>() ?: this
}