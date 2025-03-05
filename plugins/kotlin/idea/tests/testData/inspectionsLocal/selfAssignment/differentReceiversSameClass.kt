// PROBLEM: none
// Issue: KTIJ-32286

public class Node {
    internal var prev: Node? = null
    internal var next: Node? = null

    public fun pop(): Node? {
        val r = this.next
        if (this.prev != null) {
            this.prev!!.next = this.next<caret>
        }
        return r
    }
}
