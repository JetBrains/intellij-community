// PROBLEM: none
class NotCloseable {
    fun read() {
        print("read")
    }
    fun close() {
        print("close")
    }
}

fun c() {
    val s = <caret>NotCloseable()
    s.read()
    s.close()
}
