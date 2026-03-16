// PROBLEM: none
class Buffer {
    fun isNotEmpty(): Boolean = true
}

fun test3(buf: Buffer?) {
    if (<caret>buf != null && buf.isNotEmpty()) {}
}